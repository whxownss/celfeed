package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멀티 인스턴스 relay 대비 FOR UPDATE SKIP LOCKED 검증
 * 트랜잭션 2개를 동시에 열어야 해서 테스트 자체의 트랜잭션(롤백)을 끄고, 끝나면 직접 정리한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxSkipLockedTest extends SocialRepositoryTestSupport {

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    private List<Long> ids;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAllInBatch();
        ids = new TransactionTemplate(transactionManager).execute(status -> {
            List<OutboxEvent> saved = outboxRepository.saveAll(List.of(
                    OutboxEvent.pending(UUID.randomUUID().toString(), "LIKE_POST", 1L, "k", "{}"),
                    OutboxEvent.pending(UUID.randomUUID().toString(), "LIKE_POST", 2L, "k", "{}"),
                    OutboxEvent.pending(UUID.randomUUID().toString(), "LIKE_POST", 3L, "k", "{}"),
                    OutboxEvent.pending(UUID.randomUUID().toString(), "LIKE_POST", 4L, "k", "{}")
            ));
            return saved.stream().map(OutboxEvent::getId).toList();
        });
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("다른 relay 가 잠근 행은 건너뛰고 나머지만 가져온다 (같은 이벤트를 두 relay 가 중복 발행하지 않음)")
    void skipLockedRows() throws Exception {
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // relay A : 2건을 잠그고, relay B 가 조회를 끝낼 때까지 트랜잭션 유지
            Future<List<Long>> relayA = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        List<Long> locked = outboxRepository.findPendingForRelay(now, 2)
                                .stream().map(OutboxEvent::getId).toList();
                        firstLocked.countDown();
                        await(secondDone);
                        return locked;
                    }));

            await(firstLocked);

            // relay B : 같은 조건으로 조회
            List<Long> relayB = new TransactionTemplate(transactionManager).execute(status ->
                    outboxRepository.findPendingForRelay(now, 10).stream().map(OutboxEvent::getId).toList());
            secondDone.countDown();

            List<Long> lockedByA = relayA.get(30, TimeUnit.SECONDS);

            assertThat(lockedByA).containsExactly(ids.get(0), ids.get(1));
            assertThat(relayB).containsExactly(ids.get(2), ids.get(3)); // A 가 잠근 행은 건너뜀
        } finally {
            secondDone.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
