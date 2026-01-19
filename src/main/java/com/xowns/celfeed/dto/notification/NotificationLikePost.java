package com.xowns.celfeed.dto.notification;

import lombok.Getter;

@Getter
public class NotificationLikePost {
    private Long postId;
    private Long actorId;

    public NotificationLikePost(Long postId, Long actorId) {
        this.postId = postId;
        this.actorId = actorId;
    }
}
