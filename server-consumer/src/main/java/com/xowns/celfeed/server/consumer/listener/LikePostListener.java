package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikePostListener {

    private static final String EVENT_TYPE = "like-post";
    private static final String UNIQUE_KEY_NAME = Notification.UK_EVENT_RECEIVER;

    private final ObjectMapper objectMapper;
    private final IdempotencyCache idempotencyCache;
    private final NotificationSender sender;

    private final LikeRepository likeRepository;
    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopic.LIKE_POST, containerFactory = KafkaConsumerConfig.LIKE_POST_CF)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[LIKE-POST] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.error("[LIKE-POST] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        LikePostEvent event = parse(payload);

        log.info("[LIKE-POST] 페이로드 : eventId={}, likeId={}, postId={}, receiverId={}, actorId={}, occurredAt={}",
                event.getEventId(),
                event.getLikeId(),
                event.getPostId(),
                event.getReceiverId(),
                event.getActorId(),
                event.getOccurredAt());

        process(event);

        // 여기까지 와야 commit
        // 그전에 예외가 발생하면 -> Error Handler가 재시도 및 DLQ 처리
        ack.acknowledge();
    }

    private LikePostEvent parse(String payload) {
        LikePostEvent event;

        try {
            event = objectMapper.readValue(payload, LikePostEvent.class);
        } catch (JacksonException e) {
            throw new NonRetryableConsumerException("[LIKE-POST] 역직렬화 실패 : payload=" + payload, e);
        }

        if (event.getEventId() == null || event.getLikeId() == null || event.getPostId() == null
                || event.getReceiverId() == null || event.getActorId() == null || event.getOccurredAt() == null) {

            throw new NonRetryableConsumerException("[LIKE-POST] 필수 값 누락 : payload=" + payload);
        }

        return event;
    }

    private void process(LikePostEvent event) {
        String eventId = event.getEventId();

        // 1) 이미 처리된 이벤트면 skip
        if (idempotencyCache.isProcessed(EVENT_TYPE, eventId)) {
            log.info("[LIKE-POST] 이미 처리된 이벤트 skip : eventId={}", eventId);
            return;
        }

        // 2) 락 획득 (동시 처리 방지)
        IdempotencyCache.LockHandle lock = idempotencyCache.tryLock(EVENT_TYPE, eventId);

        if (lock.isHeldByOther()) {
            // skip(ack) x : 락 주인이 처리 중 죽으면 메시지 유실
            // 예외를 던져 잠시 후 재시도 -> 1에서 skip 되거나 내가 처리
            throw new RetryableConsumerException("[LIKE-POST] 다른 Consumer가 처리 중 : eventId=" + eventId);
        }

        try {
            // 3) 더블 체크 : 1과 2 사이에 다른 Consumer가 처리를 끝낸 경우
            if (idempotencyCache.isProcessed(EVENT_TYPE, eventId)) {
                log.info("[LIKE-POST] 이미 처리된 이벤트 skip (double-check) : eventId={}", eventId);
                return;
            }
            handle(event);
        } finally {
            idempotencyCache.releaseLock(lock);
        }
    }

    private void handle(LikePostEvent event) {
        String eventId = event.getEventId();
        Long likeId = event.getLikeId();

        // 4) 좋아요 조회 (없으면 skip)
        Like like = likeRepository.findGraphById(likeId).orElse(null);

        if (like == null) {
            log.warn("[LIKE-POST] 존재하지 않는 좋아요 : eventId={}, likeId={}", eventId, likeId);
            return;
        }
        String actorNickname = like.getMember().getNickname();

        // 5) 알림 저장
        Notification savedNotification;

        try {
            savedNotification = notificationService.saveLikePostNotification(
                    event.getReceiverId(), // Shard Key
                    event.getActorId(),
                    event.getPostId(),
                    eventId
            );
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                // Redis 장애 또는 TTL 만료된 경우
                log.info("[LIKE-POST] DB 유니크 키 중복, 이미 처리된 이벤트 skip : eventId={}", eventId);
                idempotencyCache.markProcessed(EVENT_TYPE, eventId);
                return;
            }
            // NOT NULL 위반 등 다른 제약 위반 -> 재시도 무의미
            throw new NonRetryableConsumerException("[LIKE-POST] 알림 저장 제약 위반 : eventId=" + eventId, e);
        }
        log.info("[LIKE-POST] 알림 저장 : id={}", savedNotification.getId());

        // 6) 처리 완료 마크
        idempotencyCache.markProcessed(EVENT_TYPE, eventId);

        // 7) 알림 전송 (best-effort)
        send(savedNotification, actorNickname);
    }

    private void send(Notification notification, String actorNickname) {
        try {
            sender.send(NotificationDto.of(notification, actorNickname));
            log.info("[LIKE-POST] 알림 전송 : id={}", notification.getId());
        } catch (Exception e) {
            // 알림은 이미 DB에 저장된 상태
            log.error("[LIKE-POST] 알림 전송 실패 : id={}", notification.getId(), e);
        }
    }

    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains(UNIQUE_KEY_NAME)) {
                return true;
            }
            t = t.getCause();
        }
        return false;

    }
}
