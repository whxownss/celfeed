package com.xowns.celfeed.server.consumer.config;

import com.xowns.celfeed.server.consumer.listener.WritePostNotiMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    public static final String LIKE_POST_CONTAINER_FACTORY = "likePostContainerFactory";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.consumers.like-post.group-id}")
    private String likePostGroupId;

    @Value("${app.kafka.consumers.write-post.group-id}")
    private String writePostGroupId;

    // ============================================================================================
    // like-post
    // ============================================================================================

    @Bean
    public ConsumerFactory<String, String> likePostConsumerFactory() {
        return createConsumerFactory(likePostGroupId, false);
    }

    @Bean(LIKE_POST_CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> likePostContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(likePostConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }

//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
//            KafkaTransactionManager<String, String> kafkaTransactionManager) {
//
//        ConcurrentKafkaListenerContainerFactory<String, String> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//
//        factory.setConsumerFactory(consumerFactory());
//        factory.getContainerProperties().setKafkaAwareTransactionManager(kafkaTransactionManager);
//
//        return factory;
//    }

    // ============================================================================================
    // write-post
    // ============================================================================================

//    @Bean
//    public ConsumerFactory<String, String> writePostConsumerFactory() {
//        Map<String, Object> props = defaultConsumerFactory();
//
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, writePostGroupId);
//        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString());
//
//        return new DefaultKafkaConsumerFactory<>(props);
//    }
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, String> writePostContainerFactory(
//            KafkaTransactionManager<String, String> kafkaTransactionManager) {
//
//        ConcurrentKafkaListenerContainerFactory<String, String> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//
//        factory.setConsumerFactory(writePostConsumerFactory());
////        factory.getContainerProperties().setKafkaAwareTransactionManager(kafkaTransactionManager);
//        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
//
//        return factory;
//    }

    // ============================================================================================
    // default
    // ============================================================================================
    private ConsumerFactory<String, String> createConsumerFactory(String groupId, boolean readCommitted) {
        Map<String, Object> props = new HashMap<>();

        // default
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        if (readCommitted) {
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString());
        }

        return new DefaultKafkaConsumerFactory<>(props);
    }
}
