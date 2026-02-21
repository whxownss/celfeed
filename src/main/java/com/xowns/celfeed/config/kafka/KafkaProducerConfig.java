package com.xowns.celfeed.config.kafka;

import com.xowns.celfeed.service.notification.kafka.WritePostNotiMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.DestinationTopic;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Long> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, LongSerializer.class);

        // default
//        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
//        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
//        configs.put(ProducerConfig.ACKS_CONFIG, "all");
//        configs.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        DefaultKafkaProducerFactory<String, Long> factory = new DefaultKafkaProducerFactory<>(configs);

        return factory;
    }

    @Bean
    public KafkaTemplate<String, Long> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, WritePostNotiMessage> wirtePostNotiProducerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        DefaultKafkaProducerFactory<String, WritePostNotiMessage> factory = new DefaultKafkaProducerFactory<>(configs);
        factory.setTransactionIdPrefix("tx-"); // 트랜잭션 기능 활성화;

        return factory;
    }

    @Bean
    public KafkaTransactionManager<String, WritePostNotiMessage> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(wirtePostNotiProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, WritePostNotiMessage> writePostNotiKafkaTemplate() {
        return new KafkaTemplate<>(wirtePostNotiProducerFactory());
    }
}
