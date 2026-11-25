package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.outbox.event.OutboxCreatedEvent;
import com.xowns.celfeed.server.social.producer.KafkaProducer;
import com.xowns.celfeed.server.social.producer.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private static final int MAX_RETRY = 5;
    private static final int MAX_BACKOFF_SEC = 300;

    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final KafkaProducer producer = mock(KafkaProducer.class);
    private final OutboxRelay relay = new OutboxRelay(outboxRepository, producer);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(relay, "batchSize", 100);
        ReflectionTestUtils.setField(relay, "maxRetry", MAX_RETRY);
        ReflectionTestUtils.setField(relay, "maxBackoffSec", MAX_BACKOFF_SEC);
    }

    private static OutboxEvent outbox(long id, String eventType, int retryCount) {
        OutboxEvent outbox = OutboxEvent.pending("event-" + id, eventType, id, "key-" + id, "{\"id\":" + id + "}");
        ReflectionTestUtils.setField(outbox, "id", id);
        ReflectionTestUtils.setField(outbox, "retryCount", retryCount);
        return outbox;
    }

    private static OutboxEvent outbox(long id) {
        return outbox(id, "LIKE_POST", 0);
    }

    private void givenOutbox(OutboxEvent outbox) {
        given(outboxRepository.findById(outbox.getId())).willReturn(Optional.of(outbox));
    }

    private void verifyNoStatusChange() {
        verify(outboxRepository, never()).markPublished(anyLong(), any());
        verify(outboxRepository, never()).markRetry(anyLong(), any(), anyInt(), any(), any());
        verify(outboxRepository, never()).markFailed(anyLong(), any(), any());
    }

    // ========================================================================================
    // 즉시 발행 (onCreated)
    // ========================================================================================

    @Nested
    @DisplayName("즉시 발행 (onCreated)")
    class OnCreated {

        @Test
        @DisplayName("이벤트 타입의 토픽으로 파티션 키, payload 를 전송하고 PUBLISHED 로 바꾼다")
        void success() {
            givenOutbox(outbox(1L, "WRITE_POST", 0));

            relay.onCreated(new OutboxCreatedEvent(1L));

            verify(producer).send("write-post", "key-1", "{\"id\":1}");
            verify(outboxRepository).markPublished(eq(1L), any());
        }

        @Test
        @DisplayName("이미 PENDING 이 아니면(fallback 이 먼저 처리) 전송하지 않는다")
        void notPending() {
            OutboxEvent published = outbox(1L);
            ReflectionTestUtils.setField(published, "status", com.xowns.celfeed.domain.social.entity.OutboxStatus.PUBLISHED);
            givenOutbox(published);

            relay.onCreated(new OutboxCreatedEvent(1L));

            verifyNoInteractions(producer);
        }

        @Test
        @DisplayName("outbox 가 없으면 아무 일도 없다")
        void notFound() {
            given(outboxRepository.findById(1L)).willReturn(Optional.empty());

            relay.onCreated(new OutboxCreatedEvent(1L));

            verifyNoInteractions(producer);
        }

        @Test
        @DisplayName("브로커 타임아웃/인터럽트는 상태를 바꾸지 않는다 (재시도 횟수 소모 없이 fallback 에 맡김)")
        void brokerFailureKeepsPending() {
            givenOutbox(outbox(1L));
            willThrow(new ProducerTimeoutException(new RuntimeException("timeout")))
                    .willThrow(new ProducerInterruptedException(new InterruptedException()))
                    .given(producer).send(any(), any(), any());

            relay.onCreated(new OutboxCreatedEvent(1L));
            relay.onCreated(new OutboxCreatedEvent(1L));

            verifyNoStatusChange();
        }

        @Test
        @DisplayName("레코드 자체 문제(재시도 불가)는 즉시 FAILED 로 바꾼다")
        void nonRetriableMarksFailed() {
            givenOutbox(outbox(1L));
            willThrow(new ProducerNonRetriableException(new IllegalArgumentException("too large")))
                    .given(producer).send(any(), any(), any());

            relay.onCreated(new OutboxCreatedEvent(1L));

            verify(outboxRepository).markFailed(eq(1L), eq("IllegalArgumentException: too large"), any());
            verify(outboxRepository, never()).markPublished(anyLong(), any());
        }

        @Test
        @DisplayName("알 수 없는 이벤트 타입(enum 변경/삭제)은 전송하지 않고 FAILED 로 바꾼다")
        void unknownEventType() {
            givenOutbox(outbox(1L, "REMOVED_TYPE", 0));

            relay.onCreated(new OutboxCreatedEvent(1L));

            verifyNoInteractions(producer);
            verify(outboxRepository).markFailed(eq(1L), startsWith("IllegalArgumentException"), any());
        }

        @Test
        @DisplayName("재시도 가능한 예외와 분류되지 않은 예외는 재시도 횟수를 올리고 백오프 후 재시도하게 한다")
        void retriableMarksRetry() {
            givenOutbox(outbox(1L, "LIKE_POST", 0));
            willThrow(new ProducerRetriableException(new RuntimeException("not leader")))
                    .willThrow(new IllegalStateException("unexpected"))
                    .given(producer).send(any(), any(), any());

            relay.onCreated(new OutboxCreatedEvent(1L));
            relay.onCreated(new OutboxCreatedEvent(1L));

            verify(outboxRepository).markRetry(eq(1L), any(), eq(MAX_RETRY), eq("RuntimeException: not leader"), any());
            verify(outboxRepository).markRetry(eq(1L), any(), eq(MAX_RETRY), eq("IllegalStateException: unexpected"), any());
        }
    }

    // ========================================================================================
    // 재시도 백오프 / 에러 메시지
    // ========================================================================================

    @Nested
    @DisplayName("재시도 백오프와 에러 메시지")
    class Backoff {

        /** 현재 재시도 횟수가 currentRetryCount 인 이벤트가 실패했을 때, 다음 재시도까지의 간격(초) */
        private long backoffSeconds(int currentRetryCount) {
            reset(outboxRepository, producer);
            givenOutbox(outbox(1L, "LIKE_POST", currentRetryCount));
            willThrow(new ProducerRetriableException(new RuntimeException("x"))).given(producer).send(any(), any(), any());

            relay.onCreated(new OutboxCreatedEvent(1L));

            ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(outboxRepository).markRetry(eq(1L), retryAt.capture(), anyInt(), any(), now.capture());
            return Duration.between(now.getValue(), retryAt.getValue()).toSeconds();
        }

        @Test
        @DisplayName("다음 재시도까지 2^(재시도 횟수) 초를 기다린다 : 2, 4, 8, 16 ...")
        void exponential() {
            assertThat(backoffSeconds(0)).isEqualTo(2);
            assertThat(backoffSeconds(1)).isEqualTo(4);
            assertThat(backoffSeconds(2)).isEqualTo(8);
            assertThat(backoffSeconds(3)).isEqualTo(16);
        }

        @Test
        @DisplayName("백오프는 최대값(max-backoff-sec)을 넘지 않는다")
        void capped() {
            assertThat(backoffSeconds(20)).isEqualTo(MAX_BACKOFF_SEC);
        }

        @Test
        @DisplayName("에러 메시지는 원인 예외 기준이고 255자로 자른다")
        void errorMessageTruncated() {
            givenOutbox(outbox(1L));
            willThrow(new ProducerRetriableException(new RuntimeException("x".repeat(1000))))
                    .given(producer).send(any(), any(), any());

            relay.onCreated(new OutboxCreatedEvent(1L));

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(outboxRepository).markRetry(eq(1L), any(), anyInt(), error.capture(), any());
            assertThat(error.getValue()).hasSize(255).startsWith("RuntimeException: xxx");
        }
    }

    // ========================================================================================
    // fallback relay
    // ========================================================================================

    @Nested
    @DisplayName("fallback relay (relayFallback)")
    class RelayFallback {

        @Test
        @DisplayName("대기 중인 이벤트를 순서대로 전송하고, 성공한 것만 한 번에 PUBLISHED 로 바꾼다")
        void publishAll() {
            given(outboxRepository.findPendingForRelay(any(), eq(100))).willReturn(List.of(outbox(1L), outbox(2L)));

            relay.relayFallback();

            var inOrder = inOrder(producer);
            inOrder.verify(producer).send("like-post", "key-1", "{\"id\":1}");
            inOrder.verify(producer).send("like-post", "key-2", "{\"id\":2}");
            verify(outboxRepository).markPublishedIn(eq(List.of(1L, 2L)), any());
        }

        @Test
        @DisplayName("대기 중인 이벤트가 없으면 아무 일도 없다")
        void empty() {
            given(outboxRepository.findPendingForRelay(any(), anyInt())).willReturn(List.of());

            relay.relayFallback();

            verifyNoInteractions(producer);
            verify(outboxRepository, never()).markPublishedIn(any(), any());
        }

        @Test
        @DisplayName("브로커 타임아웃이 나면 배치를 중단하고, 그 전까지 성공한 것만 PUBLISHED 로 바꾼다")
        void abortOnBrokerFailure() {
            given(outboxRepository.findPendingForRelay(any(), anyInt()))
                    .willReturn(List.of(outbox(1L), outbox(2L), outbox(3L)));
            doNothing()
                    .doThrow(new ProducerTimeoutException(new RuntimeException("timeout")))
                    .when(producer).send(any(), any(), any());

            relay.relayFallback();

            verify(producer, times(2)).send(any(), any(), any()); // 3번은 시도하지 않음
            verify(outboxRepository).markPublishedIn(eq(List.of(1L)), any());
            verify(outboxRepository, never()).markRetry(anyLong(), any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("레코드 문제/재시도 가능 예외는 해당 건만 처리하고 다음 이벤트를 계속 전송한다")
        void continueOnRecordFailure() {
            given(outboxRepository.findPendingForRelay(any(), anyInt()))
                    .willReturn(List.of(outbox(1L), outbox(2L), outbox(3L), outbox(4L)));
            doNothing()
                    .doThrow(new ProducerNonRetriableException(new RuntimeException("bad")))
                    .doThrow(new ProducerRetriableException(new RuntimeException("retry")))
                    .doNothing()
                    .when(producer).send(any(), any(), any());

            relay.relayFallback();

            verify(producer, times(4)).send(any(), any(), any());
            verify(outboxRepository).markFailed(eq(2L), any(), any());
            verify(outboxRepository).markRetry(eq(3L), any(), anyInt(), any(), any());
            verify(outboxRepository).markPublishedIn(eq(List.of(1L, 4L)), any());
        }

        @Test
        @DisplayName("모두 실패하면 PUBLISHED 로 바꿀 것이 없다")
        void allFailed() {
            given(outboxRepository.findPendingForRelay(any(), anyInt())).willReturn(List.of(outbox(1L)));
            willThrow(new ProducerRetriableException(new RuntimeException("retry"))).given(producer).send(any(), any(), any());

            relay.relayFallback();

            verify(outboxRepository, never()).markPublishedIn(any(), any());
        }
    }
}
