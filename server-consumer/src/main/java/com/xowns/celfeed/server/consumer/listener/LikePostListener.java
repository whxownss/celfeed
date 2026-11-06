package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
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
    private final NotificationRepository notificationRepository;

    @KafkaListener(topics = KafkaTopic.LIKE_POST, groupId = "")
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[LIKE-POST] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.warn("[LIKE-POST] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            return;
        }

        LikePostEvent event = objectMapper.readValue(payload, LikePostEvent.class);

        log.info("[LIKE-POST] 페이로드 : eventId={}, likeId={}, postId={}, receiverId={}, actorId={}, occurredAt={}",
                event.getEventId(), // 생성자 때문에 달라지는지 확인
                event.getLikeId(),
                event.getPostId(),
                event.getReceiverId(),
                event.getActorId(),
                event.getOccurredAt());

        process(event);

        ack.acknowledge(); // consumer 설정 필요
    }

    // 샤딩 처리는?
    private void process(LikePostEvent likePostEvent) {
        String eventId = likePostEvent.getEventId();
        Long likeId = likePostEvent.getLikeId();

        Like like = likeRepository.findGraphById(likeId).orElse(null); // graph로 조회 안해도 될 듯

        if (like == null) {
            log.error("[LIKE-POST] 존재하지 않는 좋아요 : eventId={}, likeId={}", eventId, likeId);
            return;
        }

        Notification notification = Notification.create(
                likePostEvent.getReceiverId(),
                likePostEvent.getActorId(),
                NotificationType.LIKE_POST,
                likePostEvent.getPostId()
        );

        notificationRepository.save(notification);

        // 알림 전송
    }
}
