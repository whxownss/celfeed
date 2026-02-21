package com.xowns.celfeed.service.eventlistener.event;

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
