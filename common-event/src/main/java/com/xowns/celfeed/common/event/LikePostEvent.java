package com.xowns.celfeed.common.event;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
public class LikePostEvent implements SocialEvent {
    private String eventId;
    private Long likeId;
    private Long postId;
    private Long receiverId;
    private Long actorId;
    private LocalDateTime occurredAt;

    protected LikePostEvent() {
    }

    public LikePostEvent(Long likeId, Long postId, Long receiverId, Long actorId) {
        this.eventId = UUID.randomUUID().toString();
        this.likeId = likeId;
        this.postId = postId;
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.occurredAt = LocalDateTime.now();
    }
}
