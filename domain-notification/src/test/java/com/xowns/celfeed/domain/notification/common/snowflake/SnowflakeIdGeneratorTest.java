package com.xowns.celfeed.domain.notification.common.snowflake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {

    private static final long CUSTOM_EPOCH = 1420070400000L; // 2015-01-01T00:00:00Z
    private static final int TIMESTAMP_SHIFT = 10 + 12;      // NODE_ID_BITS + SEQUENCE_BITS
    private static final long SEQUENCE_MASK = (1L << 12) - 1;
    private static final long NODE_ID_MASK = ((1L << 10) - 1) << 12;

    private final SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

    @Test
    @DisplayName("연속으로 생성한 id 는 모두 양수이고 항상 증가한다")
    void monotonicIncreasing() {
        long previous = generator.nextId();

        for (int i = 0; i < 20_000; i++) {
            long current = generator.nextId();
            assertThat(current).isPositive().isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    @DisplayName("같은 ms 안에서 시퀀스(4096개)를 다 써도 다음 ms 로 넘어가 중복이 생기지 않는다")
    void noDuplicateWhenSequenceOverflows() {
        int count = 50_000; // 한 ms 에 4096개를 넘기도록 충분히 많이 생성
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }

        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 생성해도 중복이 없다")
    void noDuplicateUnderConcurrency() throws Exception {
        int threads = 8;
        int perThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    List<Long> ids = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                    return ids;
                }));
            }
            start.countDown();

            Set<Long> all = new HashSet<>();
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(all).hasSize(threads * perThread);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("id 의 상위 비트는 생성 시각(커스텀 epoch 기준 ms), 노드 비트는 0 이다")
    void idStructure() {
        long before = Instant.now().toEpochMilli() - CUSTOM_EPOCH;
        long id = generator.nextId();
        long after = Instant.now().toEpochMilli() - CUSTOM_EPOCH;

        long timestamp = id >>> TIMESTAMP_SHIFT;

        assertThat(timestamp).isBetween(before, after);
        assertThat(id & NODE_ID_MASK).isZero();
        assertThat(id & SEQUENCE_MASK).isBetween(0L, SEQUENCE_MASK);
    }

    @Test
    @DisplayName("Hibernate 용 generate() 도 nextId() 와 같은 규칙으로 id 를 만든다")
    void generateDelegatesToNextId() {
        long first = generator.nextId();
        Object generated = generator.generate(null, null);

        assertThat(generated).isInstanceOf(Long.class);
        assertThat((Long) generated).isGreaterThan(first);
    }
}
