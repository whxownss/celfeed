package com.xowns.celfeed.server.social.producer;

import com.xowns.celfeed.server.social.producer.exception.ProducerInterruptedException;
import com.xowns.celfeed.server.social.producer.exception.ProducerNonRetriableException;
import com.xowns.celfeed.server.social.producer.exception.ProducerRetriableException;
import com.xowns.celfeed.server.social.producer.exception.ProducerTimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.NotLeaderOrFollowerException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Kafka 전송 결과를 OutboxRelay 가 처리하는 4가지 예외로 분류하는지 검증
 * (Timeout/Interrupted → 배치 중단, Retriable → 백오프 재시도, NonRetriable → FAILED)
 */
class KafkaProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaProducer producer = new KafkaProducer(kafkaTemplate);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "timeout", 200L);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private void givenSendFails(Throwable cause) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        given(kafkaTemplate.send("topic", "key", "payload")).willReturn(future);
    }

    /** KafkaTemplate 은 실패 원인을 KafkaProducerException 으로 감싸서 전달한다 */
    private static KafkaProducerException wrapped(Throwable cause) {
        return new KafkaProducerException(new ProducerRecord<>("topic", "key", "payload"), "send failed", cause);
    }

    @Test
    @DisplayName("전송 성공 시 토픽/키/payload 로 보내고 예외가 없다")
    void success() {
        given(kafkaTemplate.send("topic", "key", "payload"))
                .willReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() -> producer.send("topic", "key", "payload")).doesNotThrowAnyException();
        verify(kafkaTemplate).send("topic", "key", "payload");
    }

    @Test
    @DisplayName("Kafka TimeoutException 은 RetriableException 의 하위 타입이지만 타임아웃(배치 중단)으로 분류한다")
    void kafkaTimeout() {
        givenSendFails(wrapped(new TimeoutException("metadata not available")));

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerTimeoutException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("재시도 가능한 Kafka 예외(리더 변경 등)는 Retriable 로 분류한다")
    void retriable() {
        givenSendFails(wrapped(new NotLeaderOrFollowerException("leader changed")));

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerRetriableException.class)
                .hasCauseInstanceOf(NotLeaderOrFollowerException.class);
    }

    @Test
    @DisplayName("재시도해도 소용없는 예외(메시지 크기 초과 등)는 NonRetriable 로 분류한다")
    void nonRetriable() {
        givenSendFails(wrapped(new RecordTooLargeException("too large")));

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerNonRetriableException.class)
                .hasCauseInstanceOf(RecordTooLargeException.class);
    }

    @Test
    @DisplayName("KafkaProducerException 으로 감싸지지 않은 원인도 같은 기준으로 분류한다")
    void unwrappedCause() {
        givenSendFails(new NotLeaderOrFollowerException("leader changed"));

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerRetriableException.class);
    }

    @Test
    @DisplayName("설정한 시간 안에 전송 결과가 오지 않으면 타임아웃으로 분류한다")
    void sendTimeout() {
        given(kafkaTemplate.send("topic", "key", "payload")).willReturn(new CompletableFuture<>()); // 완료되지 않음

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerTimeoutException.class);
    }

    @Test
    @DisplayName("기다리는 중 인터럽트되면 Interrupted 로 분류하고, 스레드의 인터럽트 상태를 복구한다")
    void interrupted() {
        given(kafkaTemplate.send("topic", "key", "payload")).willReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> producer.send("topic", "key", "payload"))
                .isInstanceOf(ProducerInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
