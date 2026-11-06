package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.EventType;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.config.async.AsyncConfig;
import com.xowns.celfeed.server.social.producer.KafkaProducer;
import com.xowns.celfeed.server.social.producer.ProducerInterruptedException;
import com.xowns.celfeed.server.social.producer.ProducerTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        sendInternal(outbox);

        log.info("[{}] Producer 메시지 전송 성공 : eventType={}, aggregateId={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());

        int updatedRow = outboxRepository.markPublished(outbox.getId(), LocalDateTime.now());

        // relay가 먼저 처리한 경우
        if (updatedRow == 0) {
            log.info("[{}] 이미 PUBLISHED 상태 : eventType={}, aggregateId={}",
                    outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
            return;
        }

        log.info("[{}] PUBLISHED 상태 전이 완료 : eventType={}, aggregateId={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
    }

    private void sendInternal(OutboxEvent outbox) {
        producer.send(
                EventType.valueOf(outbox.getEventType()).topic(),
                outbox.getPartitionKey(),
                outbox.getPayload()
        );
    }

    // ==============================================================================================
    // ======================================= Relay Fallback =======================================
    // ==============================================================================================

    @Value("${app.outbox.relay.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay}")
    @Transactional
    public void relayFallback() {
        List<OutboxEvent> pending =
                outboxRepository.findPendingForRelay(batchSize); // 멀티 인스턴스 relay 대비 SKIP LOCKED

        if (pending.isEmpty()) return;

        log.info("[Relay Fallback] 시작 : {}건 폴링", pending.size());

        List<Long> publishedIds = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            OutboxEvent outbox = pending.get(i);

            try {
                sendInternal(outbox);
                log.info("[Relay Fallback {}] Producer 메시지 전송 성공 : eventId={}", i + 1, outbox.getEventId());

                publishedIds.add(outbox.getId());
            } catch (ProducerTimeoutException | ProducerInterruptedException e) {
                // 타임아웃은 브로커 수준 장애라 나머지도 블로킹될 것이므로, 배치를 중단해 FOR UPDATE 락과 커넥션 보유 시간을 제한
                // 인터럽트도 인터럽트 플래그가 계속 켜져 있어서 계속 예외가 발생함
                String errorMsg = e instanceof ProducerTimeoutException ? "타임아웃으로" : "인터럽트로";

                log.error("[Relay Fallback {}] {} 인한 배치 중단, 남은 {}건은 다음 폴링에서 재시도 : eventId={}",
                        i + 1, errorMsg, pending.size() - i, outbox.getEventId(), e);
                break;
            } catch (Exception e) {
                // 타임아웃이 아니면(ProducerSendException), 이 레코드만 PENDING으로 남기고 계속 진행
                log.error("[Relay Fallback {}] Outbox 발행 실패, 다음 폴링에서 재시도 : eventId={}",
                        i + 1, outbox.getEventId(), e);
            }
        }

        if (publishedIds.isEmpty()) return;

        int updated = outboxRepository.markPublishedIn(publishedIds, LocalDateTime.now());
        log.info("[Relay Fallback] 종료 : 발행 {}건, 전이 {}건", publishedIds.size(), updated);
    }
}