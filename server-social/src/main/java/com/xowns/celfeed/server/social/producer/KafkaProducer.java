package com.xowns.celfeed.server.social.producer;

import com.xowns.celfeed.server.social.producer.exception.ProducerInterruptedException;
import com.xowns.celfeed.server.social.producer.exception.ProducerNonRetriableException;
import com.xowns.celfeed.server.social.producer.exception.ProducerRetriableException;
import com.xowns.celfeed.server.social.producer.exception.ProducerTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.producer.send-timeout}")
    private long timeout;

    public void send(String topic, String partitionKey, String payload) {
        try {
            kafkaTemplate
                    .send(topic, partitionKey, payload)
                    .get(timeout, TimeUnit.MILLISECONDS); // Kafka 전송 결과 나올 때까지 기다림
            log.info("[Producer] 메시지 전송 성공 : topic={}, partitionKey={}, payload={}", topic, partitionKey, payload);
        } catch (InterruptedException e) {
            // 인터럽트 발생 (배치 중단)
            Thread.currentThread().interrupt();
            throw new ProducerInterruptedException(e);
        } catch (ExecutionException e) {
            Throwable root = (e.getCause() instanceof KafkaProducerException kpe && kpe.getCause() != null)
                    ? kpe.getCause() : e.getCause();

            // 전송 시간 초과 (배치 중단)
            if (root instanceof org.apache.kafka.common.errors.TimeoutException) {
                throw new ProducerTimeoutException(root);
            }

            // 일시 장애 (다음에 재시도)
            if (root instanceof org.apache.kafka.common.errors.RetriableException) {
                throw new ProducerRetriableException(root);
            }

            // 영구 실패 (실패 처리)
            throw new ProducerNonRetriableException(root);
        } catch (TimeoutException e) {
            // 전송 시간 초과 (배치 중단)
            throw new ProducerTimeoutException(e);
        }
    }
}
