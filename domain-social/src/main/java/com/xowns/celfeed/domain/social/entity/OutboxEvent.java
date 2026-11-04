package com.xowns.celfeed.domain.social.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = PROTECTED)
@Getter
@ToString
public class OutboxEvent {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    /** UUID, 컨슈머 멱등성 키 */
    @Column(unique = true, columnDefinition = "CHAR(36)", nullable = false)
    private String eventId;

    /** POST, LIKE, FOLLOW, ... */
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private String aggregateType;

    /** Kafka 파티션 키 */
    @Column(nullable = false)
    private Long aggregateId;

    /** 발행할 토픽 */
    @Column(columnDefinition = "VARCHAR(20)", nullable = false)
    private String topic;

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

    public static OutboxEvent create(String aggregateType,
                                     Long aggregateId,
                                     String topic,
                                     String payload) {

        OutboxEvent e = new OutboxEvent();
        e.eventId = UUID.randomUUID().toString();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.topic = topic;
        e.payload = payload;
        e.status = OutboxStatus.PENDING;

        return e;
    }
}
