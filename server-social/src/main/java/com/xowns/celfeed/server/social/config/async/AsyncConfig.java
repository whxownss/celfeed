package com.xowns.celfeed.server.social.config.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String OUTBOX_RELAY_EXECUTOR = "outboxRelayExecutor";

    @Bean(OUTBOX_RELAY_EXECUTOR)
    public Executor outboxRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4); // 다 차면 RejectedExecutionException 발생
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("outbox-relay-");
        executor.initialize();
        return executor;
    }
}
