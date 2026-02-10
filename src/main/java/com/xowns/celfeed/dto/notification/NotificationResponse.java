package com.xowns.celfeed.dto.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.notification.Notification;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationResponse {
    private Long id;
    private String message;
    private String target;
    private boolean isRead;
    private Long receiverId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private NotificationResponse(Long id, String message, String target, boolean isRead, LocalDateTime createdAt, Long receiverId) {
        this.id = id;
        this.message = message;
        this.target = target;
        this.isRead = isRead;
        this.createdAt = createdAt;
        this.receiverId = receiverId;
    }

    public static NotificationResponse of(Notification notification, String actorNickname) {
        return new NotificationResponse(
                notification.getId(),
                notification.createMessage(actorNickname),
                notification.createTarget(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReceiverId()
        );
    }
}
