package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
import com.xowns.celfeed.server.consumer.config.KafkaConsumerConfig;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikePostListener {

    private final ObjectMapper objectMapper;
    private final LikeRepository likeRepository;
    private final NotificationService notificationService;
    private final NotificationSender sender;

    @KafkaListener(topics = KafkaTopic.LIKE_POST, containerFactory = KafkaConsumerConfig.LIKE_POST_CF)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[LIKE-POST] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.warn("[LIKE-POST] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        LikePostEvent event = objectMapper.readValue(payload, LikePostEvent.class);

        log.info("[LIKE-POST] 페이로드 : eventId={}, likeId={}, postId={}, receiverId={}, actorId={}, occurredAt={}",
                event.getEventId(),
                event.getLikeId(),
                event.getPostId(),
                event.getReceiverId(),
                event.getActorId(),
                event.getOccurredAt());

        process(event, ack);
    }

    private void process(LikePostEvent event, Acknowledgment ack) {
        String eventId = event.getEventId();
        Long likeId = event.getLikeId();

        Like like = likeRepository.findGraphById(likeId).orElse(null);

        if (like == null) {
            log.error("[LIKE-POST] 존재하지 않는 좋아요 : eventId={}, likeId={}", eventId, likeId);
            ack.acknowledge();
            return;
        }

        // 알림 저장
        Notification savedNotification = notificationService.saveLikePostNotification(
                event.getReceiverId(), // Shard Key
                event.getActorId(),
                event.getPostId()
        );
        log.info("[LIKE-POST] 알림 저장 : id={}", savedNotification.getId());

        // 알림 전송
        sender.send(NotificationDto.of(savedNotification, like.getMember().getNickname()));
        log.info("[LIKE-POST] 알림 전송 : id={}", savedNotification.getId());

        ack.acknowledge();
    }
}
