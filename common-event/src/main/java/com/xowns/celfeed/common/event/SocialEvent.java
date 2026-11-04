package com.xowns.celfeed.common.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public abstract class SocialEvent {
    private final String eventId;
    private final LocalDateTime occurredAt;

    public SocialEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }
}
