package com.xowns.celfeed.service.notificationsender;

import com.xowns.celfeed.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class SyncNotificationSender implements NotificationSender {

    private final NotificationCommandService notificationCommandService;

    @Override
    public void sendWritePost(Long postId) {
        notificationCommandService.saveWritePostNotification(postId);
    }

    @Override
    public void sendLikePost(Long likeId) {
        notificationCommandService.saveLikePostNotification(likeId);
    }
}
