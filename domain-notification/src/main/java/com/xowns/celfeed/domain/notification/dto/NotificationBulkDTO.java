package com.xowns.celfeed.domain.notification.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationBulkDTO {
    private Long id;
    private String eventId;
    private Long receiverId;
    private Long actorId;
    private String type;
    private Long targetId;
    private LocalDateTime createdAt;

    public NotificationBulkDTO(String eventId, Long receiverId, Long actorId, String type, Long targetId) {
        this.eventId = eventId;
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
