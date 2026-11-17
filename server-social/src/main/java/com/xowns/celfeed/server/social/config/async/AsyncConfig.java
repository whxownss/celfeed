package com.xowns.celfeed.server.social.config.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String OUTBOX_RELAY_EXECUTOR = "outboxRelayExecutor";

    @Bean(OUTBOX_RELAY_EXECUTOR)
    public Executor outboxRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setQueueCapacity(100); // 큐까지 가득차면
        executor.setMaxPoolSize(4);     // max까지 생성, 이거마저 다 차면 RejectedExecutionException 발생
        executor.setThreadNamePrefix("outbox-relay-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("[Outbox] 비동기 스레드 풀 포화 상태, fallback에서 처리 예정 : active={}, queue={}",
                    pool.getActiveCount(), pool.getQueue().size());
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
