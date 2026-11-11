package com.xowns.celfeed.server.consumer.config;

import com.xowns.celfeed.common.event.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final long ONE_DAY =  1 * 24 * 60 * 60 * 1000L;
    private static final long ONE_HOUR =  1 * 60 * 60 * 1000L;
    private static final long ONE_MINUTE =  1 * 60 * 1000L;

    @Bean
    public NewTopic writePostFanoutTopic() {
        return TopicBuilder.name(KafkaTopic.WRITE_POST_FANOUT)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(ONE_DAY))
                .build();
    }
}
