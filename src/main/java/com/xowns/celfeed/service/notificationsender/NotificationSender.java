package com.xowns.celfeed.service.notificationsender;

public interface NotificationSender {
    void sendWritePost(Long postId);
    void sendLikePost(Long likeId);
}
