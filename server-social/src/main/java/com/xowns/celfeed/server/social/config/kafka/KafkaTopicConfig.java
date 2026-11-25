package com.xowns.celfeed.server.social.config.kafka;

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
    public NewTopic writePostTopic(
            @Value("${app.kafka.topics.write-post.partitions}") int partitions,
            @Value("${app.kafka.topics.write-post.replicas}") int replicas,
            @Value("${app.kafka.topics.write-post.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.WRITE_POST, partitions, replicas, retention);
    }

    @Bean
    public NewTopic likePostTopic(
            @Value("${app.kafka.topics.like-post.partitions}") int partitions,
            @Value("${app.kafka.topics.like-post.replicas}") int replicas,
            @Value("${app.kafka.topics.like-post.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.LIKE_POST, partitions, replicas, retention);
    }

    @Bean
    public NewTopic followMemberTopic(
            @Value("${app.kafka.topics.follow-member.partitions}") int partitions,
            @Value("${app.kafka.topics.follow-member.replicas}") int replicas,
            @Value("${app.kafka.topics.follow-member.retention}") Duration retention
    ) {
        return buildTopic(KafkaTopic.FOLLOW_MEMBER, partitions, replicas, retention);
    }

    private NewTopic buildTopic(String topic, int partitions, int replicas, Duration retention) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                .build();
    }
}
