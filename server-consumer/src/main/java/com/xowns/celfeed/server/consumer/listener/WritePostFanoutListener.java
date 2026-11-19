package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.config.KafkaConsumerConfig;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.exception.RetryableConsumerException;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WritePostFanoutListener {

    private static final String EVENT_TYPE = "write-post-fanout";

    @Value("${app.sharding.notification.mod}")
    private int mod;

    private final ObjectMapper objectMapper;
    private final FollowQueryRepository followQueryRepository;
    private final NotificationService notificationService;
    private final NotificationSender sender;
    private final IdempotencyCache idempotencyCache;

    @KafkaListener(topics = KafkaTopic.WRITE_POST_FANOUT, containerFactory = KafkaConsumerConfig.WRITE_POST_FANOUT_CF)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[WRITE-POST-FANOUT] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.error("[WRITE-POST-FANOUT] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        WritePostFanoutEvent event = parse(payload);

        log.info("[WRITE-POST-FANOUT] 페이로드 : eventId={}, postId={}, authorId={}, startId={}, endId={}",
                event.eventId(),
                event.postId(),
                event.authorId(),
                event.startFollowerId(),
                event.endFollowerId());

        process(event);

        ack.acknowledge();
    }

    private WritePostFanoutEvent parse(String payload) {
        WritePostFanoutEvent event;

        try {
            event = objectMapper.readValue(payload, WritePostFanoutEvent.class);
        } catch (JacksonException e) {
            throw new NonRetryableConsumerException("[WRITE-POST-FANOUT] 역직렬화 실패 : payload=" + payload, e);
        }

        if (event.eventId() == null || event.postId() == null || event.authorId() == null) {
            throw new NonRetryableConsumerException("[WRITE-POST-FANOUT] 필수 값 누락 : payload=" + payload);
        }

        return event;
    }

    // ============================================================================================
    // 메시지 단위 멱등 (Redis) : like-post 와 같은 패턴, 키만 "eventId + 팔로워 구간"
    // ============================================================================================

    private void process(WritePostFanoutEvent event) {
        String idempotencyKey = idempotencyKey(event);

        // 1) 이 구간을 이미 처리했으면 skip
        if (idempotencyCache.isProcessed(EVENT_TYPE, idempotencyKey)) {
            log.info("[WRITE-POST-FANOUT] 이미 처리된 구간 skip : key={}", idempotencyKey);
            return;
        }

        // 2) 락
        IdempotencyCache.LockHandle lock = idempotencyCache.tryLock(EVENT_TYPE, idempotencyKey);

        if (lock.isHeldByOther()) {
            throw new RetryableConsumerException("[WRITE-POST-FANOUT] 다른 Consumer 가 처리 중 : key=" + idempotencyKey);
        }

        try {
            // 3) 더블 체크
            if (idempotencyCache.isProcessed(EVENT_TYPE, idempotencyKey)) {
                log.info("[WRITE-POST-FANOUT] 이미 처리된 구간 skip (double-check) : key={}", idempotencyKey);
                return;
            }

            handle(event);

            // 6) 모든 샤드 저장이 끝난 뒤에만 완료 마크
            idempotencyCache.markProcessed(EVENT_TYPE, idempotencyKey);
        } finally {
            idempotencyCache.releaseLock(lock);
        }
    }

    /** 같은 게시글 이벤트라도 팔로워 구간마다 별개의 작업이므로 구간까지 키에 포함 */
    private String idempotencyKey(WritePostFanoutEvent event) {
        String start = event.startFollowerId() == null ? "first" : String.valueOf(event.startFollowerId());
        String end = event.endFollowerId() == null ? "last" : String.valueOf(event.endFollowerId());
        return event.eventId() + ":" + start + "-" + end;
    }

    // ============================================================================================
    // 팔로워 단위 멱등 (DB) : 이미 저장된 팔로워는 건너뛰고, 새로 저장된 알림만 전송
    // ============================================================================================

    private void handle(WritePostFanoutEvent event) {
        // 4) 팔로워 조회
        List<Long> followerIds =
                followQueryRepository.findFollowerIds(event.authorId(), event.startFollowerId(), event.endFollowerId());

        log.info("[WRITE-POST-FANOUT] 팔로워 조회 : {}명", followerIds.size());

        if (followerIds.isEmpty()) return; // 그 사이 언팔하는 경우

        // 5) 샤드별 저장 + 전송
        Map<Long, List<Long>> idsByShard = followerIds.stream()
                .collect(Collectors.groupingBy(followerId -> followerId % mod));

        idsByShard.forEach((shardKey, ids) -> {
            List<Notification> savedNotifications;

            try {
                savedNotifications = notificationService.saveWritePostNotification(
                        shardKey,
                        ids,
                        event.authorId(),
                        event.postId(),
                        event.eventId()
                );
            } catch (DataIntegrityViolationException e) {
                // 중복은 SQL 에서 이미 무시되므로, 여기 오는 건 NOT NULL 등 "진짜" 데이터 문제
                throw new NonRetryableConsumerException(
                        "[WRITE-POST-FANOUT] 알림 저장 제약 위반 : eventId=" + event.eventId() + ", shard=" + shardKey, e
                );
            }
            // 그 외 DB 예외(커넥션 끊김 등)는 그대로 던짐 → 재시도
            // → 이미 저장된 샤드는 다음 시도에서 "새로 저장된 것 0건"이 되어 중복 전송 안 됨

            log.info("[WRITE-POST-FANOUT] 알림 저장 : shard={}, 요청={}, 신규={}건",
                    shardKey, ids.size(), savedNotifications.size());

            // 알림 전송
            send(savedNotifications, event.authorNickname());
        });
    }

    private void send(List<Notification> notifications, String authorNickname) {
        if (notifications.isEmpty()) return;

        try {
            List<NotificationDto> sendData = notifications.stream()
                    .map(notification -> NotificationDto.of(notification, authorNickname))
                    .toList();
            sender.send(sendData);
            log.info("[WRITE-POST-FANOUT] 알림 전송 : {}건", sendData.size());
        } catch (Exception e) {
            // 알림은 이미 DB 에 저장됨 (best-effort)
            log.error("[WRITE-POST-FANOUT] 알림 전송 실패 : {}건", notifications.size(), e);
        }
    }
}
