package com.xowns.celfeed.infra.sharding.aop;

import com.xowns.celfeed.domain.notification.common.annotation.NotificationTransactional;
import com.xowns.celfeed.infra.sharding.core.Sharding;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.AopInvocationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ServiceAspect(@Sharding AOP) 동작 검증 (DB 없이 Spring AOP 프록시만 사용)
 */
@SpringJUnitConfig(ServiceAspectTest.AopTestConfig.class)
class ServiceAspectTest {

    @Autowired
    ShardedService shardedService;

    @Autowired
    TransactionalShardedService transactionalShardedService;

    @Autowired
    NotShardedService notShardedService;

    @Autowired
    RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        UserHolder.clearSharding();
        transactionManager.shardKeysAtBegin.clear();
    }

    @AfterEach
    void tearDown() {
        UserHolder.clearSharding();
    }

    // ========================================================================================
    // 기본 동작
    // ========================================================================================

    @Test
    @DisplayName("@Sharding 클래스의 메서드는 첫 번째 인자(long)를 샤드키로 설정한 상태로 실행된다")
    void setShardKeyFromFirstArgument() {
        UserHolder.Sharding sharding = shardedService.current(7L, "ignored");

        assertThat(sharding.getTarget()).isEqualTo(ShardingTarget.NOTIFICATION);
        assertThat(sharding.getShardKey()).isEqualTo(7L);
    }

    @Test
    @DisplayName("첫 번째 인자가 Long(박싱 타입)이어도 샤드키로 설정된다 (서비스 메서드들은 Long 을 사용)")
    void boxedLongArgument() {
        UserHolder.Sharding sharding = shardedService.currentBoxed(9L);

        assertThat(sharding.getShardKey()).isEqualTo(9L);
    }

    @Test
    @DisplayName("메서드가 끝나면 샤딩 정보가 해제된다")
    void clearAfterReturn() {
        shardedService.current(7L, "ignored");

        assertThat(UserHolder.getSharding()).isNull();
    }

    @Test
    @DisplayName("메서드에서 예외가 나도 샤딩 정보가 해제된다")
    void clearAfterException() {
        assertThatThrownBy(() -> shardedService.fail(7L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(UserHolder.getSharding()).isNull();
    }

    @Test
    @DisplayName("@Sharding 이 없는 클래스에는 적용되지 않는다")
    void notAppliedWithoutAnnotation() {
        assertThat(notShardedService.current(7L)).isNull();
    }

    // ========================================================================================
    // 적용되지 않는 경우 (현재 동작 기록)
    // ========================================================================================

    @Test
    @DisplayName("[현재 동작] 첫 번째 인자가 long 이 아니면 적용되지 않는다 → 이 상태로 DB 접근 시 라우터 예외")
    void notAppliedWhenFirstArgumentIsNotLong() {
        assertThat(shardedService.currentWithStringFirst("7", 7L)).isNull();
    }

    /**
     * 현재 동작 기록 : 포인트컷 args(shardKey,..) 가 null 인 Long 도 매칭하지만,
     * advice 파라미터가 primitive long 이라 null 을 넘기지 못해 메서드 실행 전에 예외가 발생한다.
     * (원인을 알기 어려운 예외라, 필요하면 Long 으로 받아 null 체크 후 명확한 예외를 던지는 방식 고려)
     */
    @Test
    @DisplayName("[현재 동작] 샤드키로 null 을 넘기면 메서드가 실행되기 전에 AopInvocationException 이 발생한다")
    void nullShardKeyFailsBeforeInvocation() {
        assertThatThrownBy(() -> shardedService.currentBoxed(null))
                .isInstanceOf(AopInvocationException.class)
                .hasMessageContaining("Mismatch on arguments to advice method");

        assertThat(UserHolder.getSharding()).isNull();
    }

    /**
     * 현재 동작 기록 (주의)
     * 안쪽 @Sharding 메서드의 finally 에서 clearSharding() 이 호출되면서, 바깥 메서드의 샤딩 정보까지 지워진다.
     * 바깥 메서드가 안쪽 호출 이후에 DB 에 접근하면 "샤딩 정보 없이 DB 접근" 예외가 발생한다.
     * 중첩 호출을 지원하려면 aspect 에서 이전 값을 저장해 두었다가 복원해야 한다.
     */
    @Test
    @DisplayName("[현재 동작] @Sharding 메서드 안에서 다른 @Sharding 메서드를 호출하면, 호출이 끝난 뒤 바깥의 샤딩 정보가 사라진다")
    void nestedCallClearsOuterSharding() {
        NestedResult result = shardedService.callInner(1L, 2L, transactionalShardedService);

        assertThat(result.beforeInner()).isEqualTo(1L);
        assertThat(result.insideInner()).isEqualTo(2L);
        assertThat(result.afterInner()).isNull(); // 1L 로 복원되지 않음
    }

    // ========================================================================================
    // 트랜잭션과의 순서
    // ========================================================================================

    @Test
    @DisplayName("샤딩 AOP 는 트랜잭션보다 먼저 실행되어, 트랜잭션 시작 시점에 이미 샤드키가 설정되어 있다")
    void runsBeforeTransaction() {
        transactionalShardedService.current(5L);

        assertThat(transactionManager.shardKeysAtBegin).containsExactly(5L);
    }

    // ========================================================================================
    // 테스트용 빈
    // ========================================================================================

    record NestedResult(Long beforeInner, Long insideInner, Long afterInner) {
    }

    private static Long currentKey() {
        UserHolder.Sharding sharding = UserHolder.getSharding();
        return sharding == null ? null : sharding.getShardKey();
    }

    @Sharding(target = ShardingTarget.NOTIFICATION)
    static class ShardedService {

        public UserHolder.Sharding current(long shardKey, String other) {
            return UserHolder.getSharding();
        }

        public UserHolder.Sharding currentBoxed(Long shardKey) {
            return UserHolder.getSharding();
        }

        public UserHolder.Sharding currentWithStringFirst(String notShardKey, long shardKey) {
            return UserHolder.getSharding();
        }

        public void fail(long shardKey) {
            throw new IllegalStateException("fail");
        }

        public NestedResult callInner(long outerKey, long innerKey, TransactionalShardedService inner) {
            Long before = currentKey();
            Long inside = inner.currentKey(innerKey);
            Long after = currentKey();
            return new NestedResult(before, inside, after);
        }
    }

    @Sharding(target = ShardingTarget.NOTIFICATION)
    @NotificationTransactional
    static class TransactionalShardedService {

        public Long current(long shardKey) {
            return ServiceAspectTest.currentKey();
        }

        public Long currentKey(long shardKey) {
            return ServiceAspectTest.currentKey();
        }
    }

    static class NotShardedService {

        public UserHolder.Sharding current(long shardKey) {
            return UserHolder.getSharding();
        }
    }

    /** 트랜잭션이 시작되는 순간(=커넥션을 얻는 시점)의 샤드키를 기록하는 가짜 트랜잭션 매니저 */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        final List<Long> shardKeysAtBegin = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            shardKeysAtBegin.add(currentKey());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class AopTestConfig {

        @Bean
        ServiceAspect serviceAspect() {
            return new ServiceAspect();
        }

        @Bean
        ShardedService shardedService() {
            return new ShardedService();
        }

        @Bean
        TransactionalShardedService transactionalShardedService() {
            return new TransactionalShardedService();
        }

        @Bean
        NotShardedService notShardedService() {
            return new NotShardedService();
        }

        /** @NotificationTransactional 이 이 이름의 트랜잭션 매니저를 사용 */
        @Bean
        RecordingTransactionManager notificationTransactionManager() {
            return new RecordingTransactionManager();
        }
    }
}
