package com.xowns.celfeed.server.social.config.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.async")
public record OutboxAsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int awaitTerminationSeconds
) {
}
