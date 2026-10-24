package com.xowns.celfeed.server.social.event.payload;

import lombok.Getter;

@Getter
public class WritePostEvent {
    private String topic;
    private Long postId;

    public WritePostEvent(String topic, Long postId) {
        this.topic = topic;
        this.postId = postId;
    }
}
