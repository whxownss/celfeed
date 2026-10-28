package com.xowns.cefeed.server.consumer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final long ONE_DAY =  1 * 24 * 60 * 60 * 1000L;
    private static final long ONE_HOUR =  1 * 60 * 60 * 1000L;
    private static final long ONE_MINUTE =  1 * 60 * 1000L;

    @Bean
    public NewTopic notiBatchTopic(@Value("${app.kafka.topics.noti-batch}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .config(
                        TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(ONE_DAY)
                )
                .build();
    }
}
