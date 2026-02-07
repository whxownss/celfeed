package com.xowns.celfeed.service.notification.kafka;

import com.xowns.celfeed.common.consts.KafkaGroupConst;
import com.xowns.celfeed.common.consts.KafkaTopicConst;
import com.xowns.celfeed.domain.basic.Like;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.notification.Notification;
import com.xowns.celfeed.domain.notification.NotificationType;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.basic.LikeRepository;
import com.xowns.celfeed.repository.notification.NotificationBulkRepository;
import com.xowns.celfeed.repository.notification.NotificationRepository;
import com.xowns.celfeed.service.basic.EmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final LikeRepository likeRepository;
    private final EmitterService emitterService;

    @KafkaListener(
            topics = KafkaTopicConst.NOTI_BATCH, concurrency = "3",
            containerFactory = "writePostNotiContainerFactory"
    )
    public void writePostNotificationListener(WritePostNotiMessage message) {
        List<NotificationBulkDTO> bulkList = message.getFollowerIds().stream()
                .map(followerId ->
                            new NotificationBulkDTO(
                                    followerId,
                                    message.getWriterId(),
                                    NotificationType.WRITE_POST.name(),
                                    message.getPostId()
                            )
                ).toList();
        List<Long> generatedKeys = notificationBulkRepository.batchInsert2(bulkList);

        List<NotificationResponse> sendData = notificationRepository.findByIdIn(generatedKeys)
                .stream().map(NotificationResponse::of).toList();

        emitterService.sendNotifications(sendData);
    }

    @KafkaListener(topics = KafkaTopicConst.LIKE_POST, groupId = KafkaGroupConst.NOTI_LIKEPOST)
    public void likePostNotificationListener(Long likeId) {
        log.info("메시지 수신={}", likeId);

        Like like = likeRepository.findGraphById(likeId).orElse(null);
        if (like == null) return;

        Member receiver = like.getPost().getMember();
        Member actor = like.getMember();
        if (actor.equals(receiver)) return;

        Notification savedNotification = notificationRepository.save(
                Notification.create(receiver, actor, NotificationType.LIKE_POST, like.getPost().getId())
        );

        emitterService.sendNotification(NotificationResponse.of(savedNotification));
    }
}
