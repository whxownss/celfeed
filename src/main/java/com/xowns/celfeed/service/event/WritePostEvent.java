package com.xowns.celfeed.service.event;

import lombok.Getter;

@Getter
public class WritePostEvent {
    private final Long postId;

    public WritePostEvent(Long postId) {
        this.postId = postId;
    }
}
