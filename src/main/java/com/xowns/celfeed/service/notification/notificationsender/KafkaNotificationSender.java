package com.xowns.celfeed.service.notification.notificationsender;

import com.xowns.celfeed.service.notification.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationSender  {

    private final NotificationCommandService notificationCommandService;

    //@KafkaListener(topics = KafkaConst.WRITE_POST, groupId = KafkaConst.WRITE_POST)
    public void sendWritePost(Long postId) {
        log.info("메시지 수신={}", postId);
        notificationCommandService.saveWritePostNotification(postId);
    }

    //@KafkaListener(topics = KafkaConst.LIKE_POST, groupId = KafkaConst.LIKE_POST)
    public void sendLikePost(Long likeId) {
        log.info("메시지 수신={}", likeId);
        notificationCommandService.saveLikePostNotification(likeId);
    }
}
