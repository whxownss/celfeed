package com.xowns.celfeed.common.event;

import lombok.Getter;

@Getter
public class WritePostEvent extends SocialEvent {
    private final Long postId;
    private final Long actorId;

    public WritePostEvent(Long postId, Long actorId) {
        super();
        this.postId = postId;
        this.actorId = actorId;
    }
}
