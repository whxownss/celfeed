package com.xowns.celfeed.server.social.event.payload;

import lombok.Getter;

@Getter
public class LikePostEvent {
    private Long likeId;

    public LikePostEvent(Long likeId) {
        this.likeId = likeId;
    }
}
