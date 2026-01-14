package com.xowns.celfeed.dto.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xowns.celfeed.domain.Notification;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationResponse {
    private Long id;
    private String message;
    private String target;
    private boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public NotificationResponse(Long id, String message, String target, boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.message = message;
        this.target = target;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.createMessage(),
                notification.createTarget(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
