package com.xowns.celfeed.service.notification.notificationsender;

import com.xowns.celfeed.service.notification.NotificationOldCommandService;
import lombok.RequiredArgsConstructor;

//@Component
@RequiredArgsConstructor
public class SyncNotificationSender implements NotificationSender {

    private final NotificationOldCommandService notificationCommandService;

    @Override
    public void sendWritePost(Long postId) {
        notificationCommandService.saveWritePostNotification(postId);
    }

    @Override
    public void sendLikePost(Long likeId) {
        notificationCommandService.saveLikePostNotification(likeId);
    }
}
