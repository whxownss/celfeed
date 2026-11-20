package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.social.entity.Follow;
import com.xowns.celfeed.domain.social.repository.FollowRepository;
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
public class FollowMemberListener {

    private static final String EVENT_TYPE = "follow-member";
    private static final String UNIQUE_KEY_NAME = Notification.UK_EVENT_RECEIVER;
    
    private final ObjectMapper objectMapper;
    private final IdempotencyCache idempotencyCache;
    private final NotificationSender sender;

    private final FollowRepository followRepository;
    private final NotificationService notificationService;
    
    @KafkaListener(topics = KafkaTopic.FOLLOW_MEMBER, containerFactory = KafkaConsumerConfig.FOLLOW_MEMBER_CF)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[FOLLOW-MEMBER] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.error("[FOLLOW-MEMBER] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        FollowMemberEvent event = parse(payload);

        log.info("[FOLLOW-MEMBER] 페이로드 : eventId={}, follow={}, fromId={}, toId={}, occurredAt={}",
                event.getEventId(),
                event.getFollowId(),
                event.getFromId(),
                event.getToId(),
                event.getOccurredAt());

        process(event);

        ack.acknowledge();
    }

    private FollowMemberEvent parse(String payload) {
        FollowMemberEvent event;

        try {
            event = objectMapper.readValue(payload, FollowMemberEvent.class);
        } catch (JacksonException e) {
            throw new NonRetryableConsumerException("[FOLLOW-MEMBER] 역직렬화 실패 : payload=" + payload, e);
        }

        if (event.getEventId() == null || event.getFollowId() == null || event.getToId() == null) {
            throw new NonRetryableConsumerException("[FOLLOW-MEMBER] 필수 값 누락 : payload=" + payload);
        }

        return event;
    }

    private void process(FollowMemberEvent event) {
        String eventId = event.getEventId();

        // 1) 이미 처리된 이벤트면 skip
        if (idempotencyCache.isProcessed(EVENT_TYPE, eventId)) {
            log.info("[FOLLOW-MEMBER] 이미 처리된 이벤트 skip : eventId={}", eventId);
            return;
        }

        // 2) 락 획득 (동시 처리 방지)
        IdempotencyCache.LockHandle lock = idempotencyCache.tryLock(EVENT_TYPE, eventId);

        if (lock.isHeldByOther()) {
            // skip(ack) x : 락 주인이 처리 중 죽으면 메시지 유실
            // 예외를 던져 잠시 후 재시도 -> 1에서 skip 되거나 내가 처리
            throw new RetryableConsumerException("[FOLLOW-MEMBER] 다른 Consumer가 처리 중 : eventId=" + eventId);
        }

        try {
            // 3) 더블 체크 : 1과 2 사이에 다른 Consumer가 처리를 끝낸 경우
            if (idempotencyCache.isProcessed(EVENT_TYPE, eventId)) {
                log.info("[FOLLOW-MEMBER] 이미 처리된 이벤트 skip (double-check) : eventId={}", eventId);
                return;
            }
            handle(event);
        } finally {
            idempotencyCache.releaseLock(lock);
        }
    }

    private void handle(FollowMemberEvent event) {
        String eventId = event.getEventId();
        Long followId = event.getFollowId();

        // 4) 팔로우 조회 (없으면 skip)
        Follow follow = followRepository.findGraphById(followId).orElse(null);

        if (follow == null) {
            log.warn("[FOLLOW-MEMBER] 존재하지 않는 팔로우 : eventId={}, followId={}", eventId, followId);
            return;
        }
        String actorNickname = follow.getFromMember().getNickname();

        // 5) 알림 저장
        Notification savedNotification;

        try {
            savedNotification = notificationService.saveFollowMemberNotification(
                    event.getToId(), // Shard Key
                    event.getFromId(),
                    event.getFollowId(),
                    eventId
            );
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                // Redis 장애 또는 TTL 만료된 경우
                log.info("[FOLLOW-MEMBER] DB 유니크 키 중복, 이미 처리된 이벤트 skip : eventId={}", eventId);
                idempotencyCache.markProcessed(EVENT_TYPE, eventId);
                return;
            }
            // NOT NULL 위반 등 다른 제약 위반 -> 재시도 무의미
            throw new NonRetryableConsumerException("[FOLLOW-MEMBER] 알림 저장 제약 위반 : eventId=" + eventId, e);
        }
        log.info("[FOLLOW-MEMBER] 알림 저장 : id={}", savedNotification.getId());

        // 6) 처리 완료 마크
        idempotencyCache.markProcessed(EVENT_TYPE, eventId);

        // 7) 알림 전송 (best-effort)
        send(savedNotification, actorNickname);
    }

    private void send(Notification notification, String actorNickname) {
        try {
            sender.send(NotificationDto.of(notification, actorNickname));
            log.info("[FOLLOW-MEMBER] 알림 전송 : id={}", notification.getId());
        } catch (Exception e) {
            // 알림은 이미 DB에 저장된 상태
            log.error("[FOLLOW-MEMBER] 알림 전송 실패 : id={}", notification.getId(), e);
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
