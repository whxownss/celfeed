package com.xowns.celfeed.server.consumer.cache;

import com.xowns.celfeed.server.consumer.cache.IdempotencyCache.LockHandle;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache.LockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

/**
 * Redis 멱등성 캐시 단위 테스트 (Redis 는 Mockito)
 * 핵심 : Redis 장애가 나도 예외를 던지지 않고 "DB 유니크 키로 방어" 하는 쪽으로 진행한다.
 */
class IdempotencyCacheTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final IdempotencyCache cache = new IdempotencyCache(redisTemplate);

    private static final RedisConnectionFailureException REDIS_DOWN = new RedisConnectionFailureException("down");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cache, "lockTtlSeconds", 20L);
        ReflectionTestUtils.setField(cache, "doneTtlHours", 24L);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Nested
    @DisplayName("처리 완료 마크")
    class Processed {

        @Test
        @DisplayName("완료 키는 idempotency:done:{타입}:{eventId} 이고, 있으면 처리된 것으로 본다")
        void isProcessed() {
            given(redisTemplate.hasKey("idempotency:done:like-post:e1")).willReturn(true);

            assertThat(cache.isProcessed("like-post", "e1")).isTrue();
            assertThat(cache.isProcessed("like-post", "e2")).isFalse();
        }

        @Test
        @DisplayName("완료 마크는 TTL(done-ttl-hours) 과 함께 저장한다")
        void markProcessed() {
            cache.markProcessed("like-post", "e1");

            verify(valueOps).set("idempotency:done:like-post:e1", "1", Duration.ofHours(24));
        }

        @Test
        @DisplayName("Redis 장애 시 조회는 '처리 안 됨' 으로 보고(DB 유니크 키로 방어), 마크 실패는 무시한다")
        void redisDown() {
            given(redisTemplate.hasKey(anyString())).willThrow(REDIS_DOWN);
            willThrow(REDIS_DOWN).given(valueOps).set(anyString(), anyString(), any(Duration.class));

            assertThat(cache.isProcessed("like-post", "e1")).isFalse();
            assertThatCode(() -> cache.markProcessed("like-post", "e1")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("처리 중 락")
    class Lock {

        @Test
        @DisplayName("락 키는 idempotency:lock:{타입}:{eventId}, 값은 매번 다른 토큰, TTL 은 lock-ttl-seconds")
        void acquire() {
            given(valueOps.setIfAbsent(eq("idempotency:lock:like-post:e1"), anyString(), eq(Duration.ofSeconds(20))))
                    .willReturn(true);

            LockHandle first = cache.tryLock("like-post", "e1");
            LockHandle second = cache.tryLock("like-post", "e1");

            assertThat(first.state()).isEqualTo(LockState.ACQUIRED);
            assertThat(first.key()).isEqualTo("idempotency:lock:like-post:e1");
            assertThat(first.token()).isNotBlank().isNotEqualTo(second.token());
            assertThat(first.isHeldByOther()).isFalse();
        }

        @Test
        @DisplayName("이미 다른 consumer 가 잡고 있으면 HELD_BY_OTHER")
        void heldByOther() {
            given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

            LockHandle lock = cache.tryLock("like-post", "e1");

            assertThat(lock.state()).isEqualTo(LockState.HELD_BY_OTHER);
            assertThat(lock.isHeldByOther()).isTrue();
        }

        @Test
        @DisplayName("Redis 장애 시 REDIS_UNAVAILABLE 로 락 없이 진행한다 (isHeldByOther = false)")
        void redisDown() {
            given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willThrow(REDIS_DOWN);

            LockHandle lock = cache.tryLock("like-post", "e1");

            assertThat(lock.state()).isEqualTo(LockState.REDIS_UNAVAILABLE);
            assertThat(lock.isHeldByOther()).isFalse();
        }

        @Test
        @DisplayName("해제는 내 토큰일 때만 지우는 Lua 스크립트로 한다 (남의 락을 지우지 않음)")
        @SuppressWarnings("unchecked")
        void releaseWithOwnToken() {
            given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).willReturn(1L);
            LockHandle lock = cache.tryLock("like-post", "e1");

            cache.releaseLock(lock);

            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(lock.key())), eq(lock.token()));
        }

        @Test
        @DisplayName("내가 잡은 락이 아니면(HELD_BY_OTHER, REDIS_UNAVAILABLE) 해제하지 않는다")
        @SuppressWarnings("unchecked")
        void releaseOnlyAcquired() {
            cache.releaseLock(new LockHandle("k", null, LockState.HELD_BY_OTHER));
            cache.releaseLock(new LockHandle("k", null, LockState.REDIS_UNAVAILABLE));

            verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        }

        @Test
        @DisplayName("락이 이미 만료됐거나 해제 중 Redis 장애가 나도 예외를 던지지 않는다")
        @SuppressWarnings("unchecked")
        void releaseFailureIgnored() {
            LockHandle lock = new LockHandle("k", "token", LockState.ACQUIRED);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .willReturn(0L)
                    .willThrow(REDIS_DOWN);

            assertThatCode(() -> cache.releaseLock(lock)).doesNotThrowAnyException();
            assertThatCode(() -> cache.releaseLock(lock)).doesNotThrowAnyException();
        }
    }
}
