package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.EventType;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.config.async.AsyncConfig;
import com.xowns.celfeed.server.social.producer.KafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaProducer producer;

    @Async(AsyncConfig.OUTBOX_RELAY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(OutboxCreatedEvent event) {
        publishOne(event.outboxId());
    }

    private void publishOne(Long outboxId) {
        outboxRepository.findById(outboxId)
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PENDING)
                .ifPresent(this::publish);
    }

    private void publish(OutboxEvent outbox) {
        producer.send(
                EventType.valueOf(outbox.getEventType()).topic(),
                outbox.getPartitionKey(),
                outbox.getPayload()
        );

        int updatedRow = outboxRepository.markPublished(outbox.getId(), LocalDateTime.now());

        if (updatedRow == 0) {
            log.info("이미 PUBLISHED 상태로 갱신된 레코드 : id={}, eventId={}, eventType={}, aggregateId={}",
                    outbox.getId(), outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
            return;
        }

        log.info("Kafka 발행 완료 : id={}, eventId={}, eventType={}, aggregateId={}",
                outbox.getId(), outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
    }
}