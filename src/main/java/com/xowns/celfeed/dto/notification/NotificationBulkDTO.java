package com.xowns.celfeed.dto.notification;

import lombok.Getter;

@Getter
public class NotificationBulkDTO {
    private Long receiverId;
    private Long actorId;
    private String type;
    private String targetType;
    private Long targetId;

    public NotificationBulkDTO(Long receiverId, Long actorId, String type, String targetType, Long targetId) {
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
