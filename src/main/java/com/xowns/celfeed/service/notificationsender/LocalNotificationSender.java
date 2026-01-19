package com.xowns.celfeed.service.notificationsender;

import com.xowns.celfeed.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class LocalNotificationSender implements NotificationSender {

    private final NotificationService notificationService;

    @Override
    public void sendWritePost(Long postId) {
        notificationService.requestWritePostNotification(postId);
    }

    @Override
    public void sendLikePost(Long postId, Long actorId) {
        notificationService.requestLikePostNotification(postId, actorId);
    }
}
