package com.xowns.celfeed.service.event;

import lombok.Getter;

@Getter
public class LikePostEvent {
    private final Long postId;
    private final Long actorId;

    public LikePostEvent(Long postId, Long actorId) {
        this.postId = postId;
        this.actorId = actorId;
    }
}
