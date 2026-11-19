package com.xowns.celfeed.server.consumer.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCache {

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final String DONE_PREFIX = "idempotency:done:";
    private static final String DONE_VALUE = "1";

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${app.kafka.consumer.lock-ttl-seconds}")
    private long lockTtlSeconds;

    @Value("${app.kafka.consumer.done-ttl-hours}")
    private long doneTtlHours;

    // ========================================================================
    // 처리 완료 마크
    // ========================================================================

    public boolean isProcessed(String eventType, String eventId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(doneKey(eventType, eventId)));
        } catch (Exception e) {
            log.warn("[Idempotency] 완료 여부 조회 실패 (DB 유니크 키로 방어) : eventType={}, eventId={}",
                    eventType, eventId, e);
            return false;
        }
    }

    public void markProcessed(String eventType, String eventId) {
        try {
            redisTemplate.opsForValue().set(doneKey(eventType, eventId), DONE_VALUE, Duration.ofHours(doneTtlHours));
        } catch (Exception e) {
            log.warn("[Idempotency] 완료 마크 실패 (DB 유니크 키로 방어) : eventType={}, eventId={}",
                    eventType, eventId, e);
        }
    }


    // ========================================================================
    // 처리 중 락
    // ========================================================================

    public LockHandle tryLock(String eventType, String eventId) {
        String key = lockKey(eventType, eventId);
        String token = UUID.randomUUID().toString();

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofSeconds(lockTtlSeconds));

            return Boolean.TRUE.equals(acquired)
                    ? new LockHandle(key, token, LockState.ACQUIRED)
                    : new LockHandle(key, null, LockState.HELD_BY_OTHER);
        } catch (Exception e) {
            log.warn("[Idempotency] 락 획득 중 Redis 장애 (락 없이 진행) : key={}", key, e);
            return new LockHandle(key, null, LockState.REDIS_UNAVAILABLE);
        }
    }

    public void releaseLock(LockHandle lock) {
        if (lock.state() != LockState.ACQUIRED) return;

        try {
            Long deleted = redisTemplate.execute(RELEASE_SCRIPT, List.of(lock.key()), lock.token);

            if (deleted == null || deleted == 0) {
                log.warn("[Idempotency] 락 만료 (처리 시간이 TTL 초과) : key={}", lock.key());
            }
        } catch (Exception e) {
            log.warn("[Idempotency] 락 해제 실패 (TTL 만료 후 자동 해제) : key={}", lock.key(), e);
        }
    }

    // ========================================================================

    private String doneKey(String eventType, String eventId) {
        return DONE_PREFIX + eventType + ":" + eventId;
    }

    private String lockKey(String eventType, String eventId) {
        return LOCK_PREFIX + eventType + ":" + eventId;
    }


    // ========================================================================

    public enum LockState {
        ACQUIRED,         // 내가 락을 잡음
        HELD_BY_OTHER,    // 다른 Consumer가 처리 중
        REDIS_UNAVAILABLE // Redis 장애 -> 락 없이 진행
    }

    public record LockHandle(String key, String token, LockState state) {
        public boolean isHeldByOther() {
            return state == LockState.HELD_BY_OTHER;
        }
    }
}
