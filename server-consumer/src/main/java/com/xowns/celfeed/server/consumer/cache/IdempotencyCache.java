package com.xowns.celfeed.server.consumer.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCache {

    private static final String LOCK_PREFIX = "notification:lock:";
    private static final String PRESENT = "1";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.kafka.consumer.lock-ttl-seconds}")
    private long lockTtlSeconds;

    public boolean tryLock(String idempotencyKey) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + idempotencyKey, PRESENT, Duration.ofSeconds(lockTtlSeconds));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Consumer Lock 획득 실패 (Skip 처리) : key={}", idempotencyKey, e);
            return false;
        }
    }

    public void releaseLock(String idempotencyKey) {
        try {
            redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
        } catch (Exception e) {
            log.warn("Consumer Lock 해제 실패 (TTL 만료 후 자동 해제) : key={}", idempotencyKey, e);
        }
    }
}
