package com.xowns.celfeed.dto.notification;

import lombok.Getter;

@Getter
public class NotificationWritePost {
    private Long postId;

    public NotificationWritePost(Long postId) {
        this.postId = postId;
    }
}
