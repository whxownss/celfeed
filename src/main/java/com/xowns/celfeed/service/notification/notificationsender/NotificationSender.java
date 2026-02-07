package com.xowns.celfeed.service.notification.notificationsender;

public interface NotificationSender {
    void sendWritePost(Long postId);
    void sendLikePost(Long likeId);
}
