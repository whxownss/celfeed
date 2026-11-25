package com.xowns.celfeed.infra.sharding.holder;

import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class UserHolderTest {

    @AfterEach
    void tearDown() {
        UserHolder.clearSharding();
    }

    @Test
    @DisplayName("설정 전에는 샤딩 정보가 없다")
    void emptyByDefault() {
        assertThat(UserHolder.getSharding()).isNull();
    }

    @Test
    @DisplayName("설정한 대상과 샤드키를 조회할 수 있다")
    void setAndGet() {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 7L);

        UserHolder.Sharding sharding = UserHolder.getSharding();
        assertThat(sharding.getTarget()).isEqualTo(ShardingTarget.NOTIFICATION);
        assertThat(sharding.getShardKey()).isEqualTo(7L);
    }

    @Test
    @DisplayName("다시 설정하면 이전 값을 덮어쓴다")
    void overwrite() {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 1L);
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 2L);

        assertThat(UserHolder.getSharding().getShardKey()).isEqualTo(2L);
    }

    @Test
    @DisplayName("clear 하면 샤딩 정보가 사라진다")
    void clear() {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 1L);

        UserHolder.clearSharding();

        assertThat(UserHolder.getSharding()).isNull();
    }

    @Test
    @DisplayName("스레드마다 샤딩 정보가 따로 관리된다")
    void threadIsolation() throws Exception {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 1L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // 다른 스레드에서는 보이지 않음
            UserHolder.Sharding inOtherThread = executor.submit(UserHolder::getSharding).get(5, TimeUnit.SECONDS);
            assertThat(inOtherThread).isNull();

            // 다른 스레드에서 설정해도 현재 스레드는 영향 없음
            executor.submit(() -> UserHolder.setSharding(ShardingTarget.NOTIFICATION, 99L)).get(5, TimeUnit.SECONDS);
            assertThat(UserHolder.getSharding().getShardKey()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }
}
