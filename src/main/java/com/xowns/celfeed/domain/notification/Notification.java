package com.xowns.celfeed.domain.notification;

import com.xowns.celfeed.common.converter.BooleanToYNConverter;
import com.xowns.celfeed.common.snowflake.SnowflakeId;
import com.xowns.celfeed.domain.basic.BaseCreateEntity;
import com.xowns.celfeed.domain.basic.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static jakarta.persistence.FetchType.*;
import static lombok.AccessLevel.PROTECTED;

@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter @ToString
public class Notification extends BaseCreateEntity {

    @Id @SnowflakeId
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

    @Column(nullable = false)
    private Long targetId;

    @Column(columnDefinition = "VARCHAR(1)", nullable = false)
    @Convert(converter = BooleanToYNConverter.class)
    private boolean isRead;

    private Notification(Member receiver, Member actor, NotificationType type, Long targetId, boolean isRead) {
        this.receiver = receiver;
        this.actor = actor;
        this.type = type;
        this.targetId = targetId;
        this.isRead = isRead;
    }

    public static Notification create(Member receiver, Member actor, NotificationType type, Long targetId) {
        return new Notification(receiver, actor, type, targetId, false);
    }

    public String createMessage() {
        return "[" + actor.getNickname() + "]" + type.getMessage();
    }

    public String createTarget() {
        return type.getTargetURI() + targetId;
    }

    public void read() {
        isRead = true;
    }
}
