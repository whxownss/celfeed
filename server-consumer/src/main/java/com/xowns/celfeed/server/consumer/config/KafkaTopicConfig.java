package com.xowns.celfeed.server.consumer.config;

import com.xowns.celfeed.common.event.KafkaTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic writePostFanoutTopic(
            @Value("${app.kafka.topics.write-post-fanout.partitions}") int partitions,
            @Value("${app.kafka.topics.write-post-fanout.replicas}") int replicas,
            @Value("${app.kafka.topics.write-post-fanout.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.WRITE_POST_FANOUT, partitions, replicas, retention);
    }

    @Bean
    public NewTopic likePostDltTopic(
            @Value("${app.kafka.topics.like-post-dlt.partitions}") int partitions,
            @Value("${app.kafka.topics.like-post-dlt.replicas}") int replicas,
            @Value("${app.kafka.topics.like-post-dlt.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.LIKE_POST_DLT, partitions, replicas, retention);
    }

    @Bean
    public NewTopic writePostDltTopic(
            @Value("${app.kafka.topics.write-post-dlt.partitions}") int partitions,
            @Value("${app.kafka.topics.write-post-dlt.replicas}") int replicas,
            @Value("${app.kafka.topics.write-post-dlt.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.WRITE_POST_DLT, partitions, replicas, retention);
    }

    @Bean
    public NewTopic writePostFanoutDltTopic(
            @Value("${app.kafka.topics.write-post-fanout-dlt.partitions}") int partitions,
            @Value("${app.kafka.topics.write-post-fanout-dlt.replicas}") int replicas,
            @Value("${app.kafka.topics.write-post-fanout-dlt.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.WRITE_POST_FANOUT_DLT, partitions, replicas, retention);
    }

    @Bean
    public NewTopic followMemberDltTopic(
            @Value("${app.kafka.topics.follow-member-dlt.partitions}") int partitions,
            @Value("${app.kafka.topics.follow-member-dlt.replicas}") int replicas,
            @Value("${app.kafka.topics.follow-member-dlt.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.FOLLOW_MEMBER_DLT, partitions, replicas, retention);
    }

    private NewTopic buildTopic(String topic, int partitions, int replicas, Duration retention) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                .build();
    }
}
