package com.xowns.celfeed.domain.notification.entity;

import com.xowns.celfeed.domain.notification.common.converter.BooleanToYNConverter;
import com.xowns.celfeed.domain.notification.common.snowflake.SnowflakeId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Notification {

    @Id @SnowflakeId
    private Long id;

//    @ManyToOne(fetch = LAZY)
//    @JoinColumn(name = "receiver_id", nullable = false)
//    private Member receiver;
    private Long receiverId;

//    @ManyToOne(fetch = LAZY)
//    @JoinColumn(name = "actor_id", nullable = false)
//    private Member actor;
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private Long targetId;

    @Column(columnDefinition = "VARCHAR(1)", nullable = false)
    @Convert(converter = BooleanToYNConverter.class)
    private boolean isRead;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // 수정일은?

    @PrePersist
    public void prePersistCreatedAt() {
        createdAt = LocalDateTime.now();
    }

    private Notification(Long receiverId, Long actorId, NotificationType type, Long targetId, boolean isRead) {
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
        this.isRead = isRead;
    }

    public static Notification create(Long receiverId, Long actorId, NotificationType type, Long targetId) {
        return new Notification(receiverId, actorId, type, targetId, false);
    }

    public String createMessage(String actorNickname) {
        return "[" + actorNickname + "]" + type.getMessage();
    }

    public String createTarget() {
        return type.getTargetURI() + targetId;
    }

    public void read() {
        isRead = true;
    }
}
