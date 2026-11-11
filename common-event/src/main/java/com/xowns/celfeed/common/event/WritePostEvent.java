package com.xowns.celfeed.common.event;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
public class WritePostEvent implements SocialEvent {
    private String eventId;
    private Long postId;
    private Long actorId;
    private LocalDateTime occurredAt;

    protected WritePostEvent() {
    }

    public WritePostEvent(Long postId, Long actorId) {
        this.eventId = UUID.randomUUID().toString();
        this.postId = postId;
        this.actorId = actorId;
        this.occurredAt = LocalDateTime.now();
    }
}
