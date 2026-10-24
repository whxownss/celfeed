package com.xowns.celfeed.server.social.event.payload;

import lombok.Getter;

@Getter
public class LikePostEvent {
    private String topic;
    private Long likeId;

    public LikePostEvent(String topic, Long likeId) {
        this.topic = topic;
        this.likeId = likeId;
    }
}
