package com.xowns.celfeed.server.consumer.config;

import com.xowns.celfeed.server.consumer.listener.WritePostFanoutEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ProducerFactory<String, WritePostFanoutEvent> producerFactory() {
        DefaultKafkaProducerFactory<String, WritePostFanoutEvent> factory =
                new DefaultKafkaProducerFactory<>(new HashMap<>(kafkaProperties.buildProducerProperties()));

        factory.setTransactionIdPrefix("tx-"); // 트랜잭션 기능 활성화

        return factory;
    }

    @Bean
    public KafkaTemplate<String, WritePostFanoutEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaTransactionManager<String, WritePostFanoutEvent> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(producerFactory());
    }
}
