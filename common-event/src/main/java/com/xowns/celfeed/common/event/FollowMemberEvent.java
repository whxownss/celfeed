package com.xowns.celfeed.common.event;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
public final class FollowMemberEvent implements SocialEvent {
    private String eventId;
    private Long followId;
    private Long fromId;
    private Long toId;
    private LocalDateTime occurredAt;

    protected FollowMemberEvent() {
    }

    public FollowMemberEvent(Long followId, Long fromId, Long toId) {
        this.eventId = UUID.randomUUID().toString();
        this.followId = followId;
        this.fromId = fromId;
        this.toId = toId;
        this.occurredAt = LocalDateTime.now();
    }
}
