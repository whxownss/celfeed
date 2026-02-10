package com.xowns.celfeed.dto.notification;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationBulkDTO {
    private Long id;
    private Long receiverId;
    private Long actorId;
    private String type;
    private Long targetId;
    private LocalDateTime createdAt;

    public NotificationBulkDTO(Long receiverId, Long actorId, String type, Long targetId) {
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
