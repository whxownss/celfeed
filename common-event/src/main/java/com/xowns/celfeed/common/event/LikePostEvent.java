package com.xowns.celfeed.common.event;

import lombok.Getter;

@Getter
public class LikePostEvent extends SocialEvent {
    private final Long likeId;
    private final Long postId;
    private final Long receiverId;
    private final Long actorId;

    public LikePostEvent(Long likeId, Long postId, Long receiverId, Long actorId) {
        super();
        this.likeId = likeId;
        this.postId = postId;
        this.receiverId = receiverId;
        this.actorId = actorId;
    }
}
