package com.xowns.celfeed.domain;

import com.xowns.celfeed.common.converter.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static jakarta.persistence.FetchType.*;
import static jakarta.persistence.GenerationType.*;
import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Notification extends BaseCreateEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private Member actor;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(10)", nullable = false)
    private NotificationTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(columnDefinition = "VARCHAR(1)", nullable = false)
    @Convert(converter = BooleanToYNConverter.class)
    private boolean isRead;

    private Notification(Member receiver, Member actor, NotificationType type, NotificationTargetType targetType, Long targetId, boolean isRead) {
        this.receiver = receiver;
        this.actor = actor;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.isRead = isRead;
    }

    public static Notification create(Member receiver, Member actor, NotificationType type, NotificationTargetType targetType, Long targetId) {
        return new Notification(receiver, actor, type, targetType, targetId, false);
    }
}
