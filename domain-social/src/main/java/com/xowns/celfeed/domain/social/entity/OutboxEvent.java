package com.xowns.celfeed.domain.social.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
        name = "outbox_event",
        indexes = @Index(name = "idx_outbox_status_craeted", columnList = "status, created_at")
)
@NoArgsConstructor(access = PROTECTED)
@Getter
@ToString
public class OutboxEvent {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    /** UUID(멱등성 키) */
    @Column(unique = true, columnDefinition = "CHAR(36)", nullable = false)
    private String eventId;

    /** 이벤트 타입(토픽) */
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private String eventType;

    /** 비즈니스ID */
    @Column(nullable = false)
    private Long aggregateId;

    /** 파티션 키 */
    @Column(columnDefinition = "VARCHAR(100)", nullable = false)
    private String partitionKey;

    /** 이벤트 본문 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /** PENDING, PUBLISHED, FAILED */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private OutboxStatus status;

    /** 발행일자 */
    @Column(nullable = true)
    private LocalDateTime publishedAt;

    /** 생성일자 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정일자 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static OutboxEvent pending(String eventId,
                                      String eventType,
                                      Long aggregateId,
                                      String partitionKey,
                                      String payload) {

        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.eventType = eventType;
        e.aggregateId = aggregateId;
        e.partitionKey = partitionKey;
        e.payload = payload;
        e.status = OutboxStatus.PENDING;

        return e;
    }
}
