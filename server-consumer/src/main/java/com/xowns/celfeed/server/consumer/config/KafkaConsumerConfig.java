package com.xowns.celfeed.server.consumer.config;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.listener.WritePostFanoutEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    public static final String LIKE_POST_CF = "likePostContainerFactory";
    public static final String WRITE_POST_CF = "writePostContainerFactory";
    public static final String WRITE_POST_FANOUT_CF = "writePostFanoutContainerFactory";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ============================================================================================
    // like-post
    // ============================================================================================

    @Value("${app.kafka.consumers.like-post.group-id}")
    private String likePostGroupId;

    @Bean
    public ConsumerFactory<String, String> likePostConsumerFactory() {
        return createConsumerFactory(
                likePostGroupId,
                false,
                new StringDeserializer()
        );
    }

    @Bean(LIKE_POST_CF)
    public ConcurrentKafkaListenerContainerFactory<String, String> likePostContainerFactory(
            KafkaTemplate<String, String> dltKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                createContainerFactory(likePostConsumerFactory(), 1, null);
        factory.setCommonErrorHandler(likePostErrorHandler(dltKafkaTemplate));

        return factory;
    }

    private DefaultErrorHandler likePostErrorHandler(KafkaTemplate<String, String> dltKafkaTemplate) {
        // 재시도를 다 써도 실패하면 DLQ로 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) ->
                        new TopicPartition(KafkaTopic.LIKE_POST_DLT, record.partition())
        );

        // 1초, 2초, 4초 간격으로 최대 3번 재시도
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(NonRetryableConsumerException.class);
        errorHandler.setCommitRecovered(true); // DLQ에 보내고 기존 메시지의 offset 커밋

        return errorHandler;
    }

    // ============================================================================================
    // write-post
    // ============================================================================================

    @Value("${app.kafka.consumers.write-post.group-id}")
    private String writePostGroupId;

    @Bean
    public ConsumerFactory<String, String> writePostConsumerFactory() {
        return createConsumerFactory(
                writePostGroupId,
                false,
                new StringDeserializer()
        );
    }

    @Bean(WRITE_POST_CF)
    public ConcurrentKafkaListenerContainerFactory<String, String> writePostContainerFactory(
            KafkaTransactionManager<String, WritePostFanoutEvent> kafkaTransactionManager
    ) {
        return createContainerFactory(
                writePostConsumerFactory(),
                1,
                kafkaTransactionManager
        );
    }

    // ============================================================================================
    // write-post-fanout
    // ============================================================================================

    @Value("${app.kafka.consumers.write-post-fanout.group-id}")
    private String writePostFanoutGroupId;

    @Bean
    public ConsumerFactory<String, WritePostFanoutEvent> writePostFanoutConsumerFactory() {
        return createConsumerFactory(
                writePostFanoutGroupId,
                true,
                new JacksonJsonDeserializer<>(WritePostFanoutEvent.class)
        );
    }

    @Bean(WRITE_POST_FANOUT_CF)
    public ConcurrentKafkaListenerContainerFactory<String, WritePostFanoutEvent> writePostFanoutContainerFactory() {
        return createContainerFactory(
                writePostFanoutConsumerFactory(),
                3,
                null
        );
    }

    // ============================================================================================
    // default
    // ============================================================================================

    private <T> ConsumerFactory<String, T> createConsumerFactory(
            String groupId,
            boolean readCommitted,
            Deserializer<T> valueDeserializer
    ) {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        if (readCommitted) {
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString());
        }

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    private <T, V> ConcurrentKafkaListenerContainerFactory<String, T> createContainerFactory(
            ConsumerFactory<String, T> consumerFactory,
            int concurrency,
            KafkaTransactionManager<String, V> kafkaTransactionManager
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        if (kafkaTransactionManager != null) {
            factory.getContainerProperties().setKafkaAwareTransactionManager(kafkaTransactionManager);
        }

        return factory;
    }
}
