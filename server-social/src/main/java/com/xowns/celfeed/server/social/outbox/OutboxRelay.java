package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.EventType;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.config.async.AsyncConfig;
import com.xowns.celfeed.server.social.producer.KafkaProducer;
import com.xowns.celfeed.server.social.producer.exception.ProducerInterruptedException;
import com.xowns.celfeed.server.social.producer.exception.ProducerNonRetriableException;
import com.xowns.celfeed.server.social.producer.exception.ProducerTimeoutException;
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

    private enum FailureAction { ABORT, CONTINUE }

    // ==============================================================================================
    // =======================================    즉시 발행    =======================================
    // ==============================================================================================

    @Async(AsyncConfig.OUTBOX_RELAY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(OutboxCreatedEvent event) {
        log.info("[Outbox] 이벤트 감지 : id={}", event.outboxId());

        outboxRepository.findById(event.outboxId())
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PENDING)
                .ifPresent(outbox -> {
                    try {
                        sendInternal(outbox);
                        markPublished(outbox);
                    } catch (RuntimeException e) {
                        // 즉시 발행에서 ABORT는 fallback에게 맡긴다는 의미 (상태 변경 x)
                        handleFailure(outbox, e, "[Outbox]");
                    }
                });
    }

    private void markPublished(OutboxEvent outbox) {
        int updatedRow = outboxRepository.markPublished(outbox.getId(), LocalDateTime.now());

        // relay가 먼저 처리한 경우
        if (updatedRow == 0) {
            log.info("[Outbox] 이미 Relay로 인해 PUBLISHED 상태 전이 완료 : eventId={}, eventType={}, aggregateId={}",
                    outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
            return;
        }

        log.info("[Outbox] PUBLISHED 상태 전이 완료 : eventId={}, eventType={}, aggregateId={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
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
                outboxRepository.findPendingForRelay(LocalDateTime.now(), batchSize);

        if (pending.isEmpty()) return;

        log.info("[Relay Fallback] 시작 : {}건 폴링", pending.size());

        List<Long> publishedIds = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            OutboxEvent outbox = pending.get(i);
            String tag = "[Relay Fallback " + (i + 1) +"]";

            try {
                sendInternal(outbox);
                publishedIds.add(outbox.getId());
            } catch (RuntimeException e) {
                if (handleFailure(outbox, e, tag) == FailureAction.ABORT) {
                    log.error("{} 배치 중단, 남은 {}건은 다음 폴링에서 재시도", tag, pending.size() - i);
                    break;
                }
            }

//            catch (ProducerTimeoutException | ProducerInterruptedException e) {
//                // 타임아웃은 브로커 수준 장애라 나머지도 블로킹될 것이므로, 배치를 중단해 FOR UPDATE 락과 커넥션 보유 시간을 제한
//                // 인터럽트도 인터럽트 플래그가 계속 켜져 있어서 계속 예외가 발생함
//                String errorMsg = e instanceof ProducerTimeoutException ? "타임아웃으로" : "인터럽트로";
//
//                log.error("[Relay Fallback {}] {} 인한 배치 중단, 남은 {}건은 다음 폴링에서 재시도 : eventId={}",
//                        i + 1, errorMsg, pending.size() - i, outbox.getEventId(), e);
//                break;
//            } catch (Exception e) {
//                // 타임아웃이 아니면(ProducerSendException), 이 레코드만 PENDING으로 남기고 계속 진행
//                log.error("[Relay Fallback {}] Outbox 발행 실패, 다음 폴링에서 재시도 : eventId={}",
//                        i + 1, outbox.getEventId(), e);
//            }
        }

        if (publishedIds.isEmpty()) return;

        int updated = outboxRepository.markPublishedIn(publishedIds, LocalDateTime.now());
        log.info("[Relay Fallback] 종료 : 발행 {}건, 전이 {}건", publishedIds.size(), updated);
    }

    // ==============================================================================================
    // =======================================     Common     =======================================
    // ==============================================================================================

    private void sendInternal(OutboxEvent outbox) {
        String topic;
        try {
            topic = EventType.valueOf(outbox.getEventType()).topic();
        } catch (IllegalArgumentException e) {
            // enum 변경/삭제 등 -> 레코드 자체 문제
            throw new ProducerNonRetriableException(e);
        }
        producer.send(topic, outbox.getPartitionKey(), outbox.getPayload());
    }

    private FailureAction handleFailure(OutboxEvent outbox, RuntimeException e, String tag) {
        LocalDateTime now = LocalDateTime.now();
        String error = errorMessage(e);

        // case 1) 브로커 장애 -> retryCount 증가 x, 배치 중단
        if (e instanceof ProducerTimeoutException || e instanceof ProducerInterruptedException) {
            log.error("{} 브로커 장애 : eventId={}, error={}", tag, outbox.getEventId(), error, e);
            return FailureAction.ABORT;
        }

        // case 2) 레코드 자체 문제 -> 즉시 FAILED
        if (e instanceof ProducerNonRetriableException) {
            outboxRepository.markFailed(outbox.getId(), error, now);
            log.error("{} 레코드 영구 실패로 FAILED 전이 : eventId={}, error={}", tag, outbox.getEventId(), error, e);
            return FailureAction.CONTINUE;
        }

        // case 3) 재시도 가능 + 분류되지 않은 예외 -> 백오프 후 재시도, 상한 도달 시 FAILED
        int nextCount = outbox.getRetryCount() + 1;
        LocalDateTime nextRetryAt = now.plusSeconds(backoffSeconds(nextCount));

        outboxRepository.markRetry(outbox.getId(), nextRetryAt, maxRetry, error, now);

        if (nextCount >= maxRetry) {
            log.error("{} 재시도 한도({}) 초과로 FAILED 전이 : eventId={}", tag, maxRetry, outbox.getEventId(), e);
        } else {
            log.warn("{} 재시도 예약 ({}/{}) : eventId={}, nextRetryAt={}",
                    tag, nextCount, maxRetry, outbox.getEventId(), nextRetryAt, e);
        }

        return FailureAction.CONTINUE;
    }

    @Value("${app.outbox.relay.max-retry}")
    private int maxRetry;

    @Value("${app.outbox.relay.max-backoff-sec}")
    private int maxBackoffSec;

    private long backoffSeconds(int retryCount) {
        return Math.min(1L << retryCount, maxBackoffSec);
    }

    private static final int MAX_ERROR_LENGTH = 255;

    private String errorMessage(Throwable e) {
        Throwable root = e.getCause() != null ? e.getCause() : e;
        String msg = root.getClass().getSimpleName() + ": " + root.getMessage();
        return msg.length() > MAX_ERROR_LENGTH ? msg.substring(0, MAX_ERROR_LENGTH) : msg;
    }
}