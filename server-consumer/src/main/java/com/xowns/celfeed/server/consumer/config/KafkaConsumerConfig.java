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
import org.springframework.kafka.listener.DefaultAfterRollbackProcessor;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.BackOff;

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
        return createConsumerFactory(likePostGroupId, false);
    }

    @Bean(LIKE_POST_CF)
    public ConcurrentKafkaListenerContainerFactory<String, String> likePostContainerFactory(
            KafkaTemplate<String, String> dltKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                createContainerFactory(likePostConsumerFactory(), 1, null);
        factory.setCommonErrorHandler(errorHandler(dltKafkaTemplate, KafkaTopic.LIKE_POST_DLT));

        return factory;
    }

    // ============================================================================================
    // write-post
    // ============================================================================================

    @Value("${app.kafka.consumers.write-post.group-id}")
    private String writePostGroupId;

    @Bean
    public ConsumerFactory<String, String> writePostConsumerFactory() {
        return createConsumerFactory(writePostGroupId, false);
    }

    @Bean(WRITE_POST_CF)
    public ConcurrentKafkaListenerContainerFactory<String, String> writePostContainerFactory(
            KafkaTransactionManager<String, WritePostFanoutEvent> kafkaTransactionManager,
            KafkaTemplate<String, WritePostFanoutEvent> kafkaTemplate,
            KafkaTemplate<String, String> dltKafkaTemplate

    ) {
        // 해당 컨테이너에서는 트랜잭션으로 커밋되기 때문에 ack.acknowledge(), MANUAL_IMMEDIATE 등이 사실상 무시됨
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                createContainerFactory(writePostConsumerFactory(), 1, kafkaTransactionManager);

        // Kafka 트랜잭션을 사용하기 때문에, DefaultErrorHandler를 붙이는 방식이 아닌,
        // 트랜잭션 롤백 뒤에 동작하는 AfterRollbackProcessor로 재시도와 DLQ 설정
        DefaultAfterRollbackProcessor<String, String> afterRollbackProcessor =
                new DefaultAfterRollbackProcessor<>(
                        deadLetterRecoverer(dltKafkaTemplate, KafkaTopic.WRITE_POST_DTL),
                        retryBackOff(),
                        kafkaTemplate, // 트랜잭션용 템플릿 : DLQ로 보낸 메시지의 offset을 트랜잭션으로 커밋
                        true           // commitRecovered
                );
        afterRollbackProcessor.addNotRetryableExceptions(NonRetryableConsumerException.class);

        factory.setAfterRollbackProcessor(afterRollbackProcessor);

        return factory;
    }

    // ============================================================================================
    // write-post-fanout
    // ============================================================================================

    @Value("${app.kafka.consumers.write-post-fanout.group-id}")
    private String writePostFanoutGroupId;

    @Bean
    public ConsumerFactory<String, String> writePostFanoutConsumerFactory() {
        return createConsumerFactory(writePostFanoutGroupId, true);
    }

    @Bean(WRITE_POST_FANOUT_CF)
    public ConcurrentKafkaListenerContainerFactory<String, String> writePostFanoutContainerFactory(
            KafkaTemplate<String, String> dltKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                createContainerFactory(writePostFanoutConsumerFactory(), 3, null);
        factory.setCommonErrorHandler(errorHandler(dltKafkaTemplate, KafkaTopic.WRITE_POST_FANOUT_DLT));

        return factory;
    }

    // ============================================================================================
    // default
    // ============================================================================================

    private <T> ConsumerFactory<String, T> createConsumerFactory(String groupId, boolean readCommitted) {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 역직렬화 실패 무한 루프 방지 및 DLQ 템플릿과 타입 호환을 위해, 전부 String 역직렬화로 고정
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        if (readCommitted) {
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString());
        }


        return new DefaultKafkaConsumerFactory<>(props);
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

    private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> dltKafkaTemplate, String dltTopic) {
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(deadLetterRecoverer(dltKafkaTemplate, dltTopic), retryBackOff());
        errorHandler.addNotRetryableExceptions(NonRetryableConsumerException.class);
        errorHandler.setCommitRecovered(true); // DLQ에 보내고 기존 메시지의 offset 커밋
        return errorHandler;
    }

    private DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> dltKafkaTemplate,
                                                              String dltTopic) {
        // 재시도를 다 써도 실패하면 DLQ로 전송
        return new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) -> new TopicPartition(dltTopic, record.partition())
        );
    }

    private BackOff retryBackOff() {
        // 1초 → 2초 → 4초, 최대 3번
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);
        return backOff;
    }
}
