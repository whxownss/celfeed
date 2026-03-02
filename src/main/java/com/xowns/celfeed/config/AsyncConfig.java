package com.xowns.celfeed.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(10)
                .maxPoolSize(10)
                .threadNamePrefix("async-")
                .awaitTermination(true)
                .awaitTerminationPeriod(Duration.ofSeconds(60))
                .build();
    }
}
