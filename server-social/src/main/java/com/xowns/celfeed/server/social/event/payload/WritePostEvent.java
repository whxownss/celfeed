package com.xowns.celfeed.server.social.event.payload;

import lombok.Getter;

@Getter
public class WritePostEvent {
    private Long postId;

    public WritePostEvent(Long postId) {
        this.postId = postId;
    }
}
