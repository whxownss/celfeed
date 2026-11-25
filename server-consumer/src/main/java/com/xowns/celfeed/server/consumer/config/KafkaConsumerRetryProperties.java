package com.xowns.celfeed.server.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.kafka.consumer.retry")
public record KafkaConsumerRetryProperties(
        int maxRetries,
        Duration initialInterval,
        double multiplier,
        Duration maxInterval
) {
}
