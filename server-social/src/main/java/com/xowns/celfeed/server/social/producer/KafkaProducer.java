package com.xowns.celfeed.server.social.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        } catch (InterruptedException e) {
            // 인터럽트 발생
            Thread.currentThread().interrupt();
            throw new ProducerSendException(e);
        } catch (ExecutionException e) {
            // 전송 실패
            throw new ProducerSendException(e.getCause());
        } catch (TimeoutException e) {
            // 전송 시간 초과
            throw new ProducerSendException(e);
        }
    }
}
