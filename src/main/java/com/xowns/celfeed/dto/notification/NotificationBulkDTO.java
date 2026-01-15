package com.xowns.celfeed.dto.notification;

import lombok.Getter;

@Getter
public class NotificationBulkDTO {
    private Long receiverId;
    private Long actorId;
    private String type;
    private Long targetId;

    public NotificationBulkDTO(Long receiverId, Long actorId, String type, Long targetId) {
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
    }
}
