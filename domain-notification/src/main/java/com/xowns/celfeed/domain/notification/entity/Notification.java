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
@Table(uniqueConstraints =
        @UniqueConstraint(
                name = Notification.UK_EVENT_RECEIVER,
                columnNames = {"event_id", "receiver_id"}
        ))
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Notification {

    public static final String UK_EVENT_RECEIVER = "uk_notification_event_receiver";

    @Id @SnowflakeId
    private Long id;

    @Column(columnDefinition = "CHAR(36)", nullable = false)
    private String eventId; // eventId + receiverId 유니크 키 추가

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

    private Notification(String eventId,
                         Long receiverId,
                         Long actorId,
                         NotificationType type,
                         Long targetId,
                         boolean isRead) {

        this.eventId = eventId;
        this.receiverId = receiverId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
        this.isRead = isRead;
    }

    public static Notification create(String eventId,
                                      Long receiverId,
                                      Long actorId,
                                      NotificationType type,
                                      Long targetId) {

        return new Notification(eventId, receiverId, actorId, type, targetId, false);
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
