package com.xowns.celfeed.infra.sharding.router;

import com.xowns.celfeed.infra.sharding.core.ShardingProperty;
import com.xowns.celfeed.infra.sharding.core.ShardingPropertyMap;
import com.xowns.celfeed.infra.sharding.core.ShardingStrategy;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 샤드 DataSource 를 Mockito 로 대신해서, 샤드키에 따라 어느 DataSource 의 커넥션이 반환되는지 검증
 */
class DataSourceRouterTest {

    private final DataSource shard0 = mock(DataSource.class);
    private final DataSource shard1 = mock(DataSource.class);
    private final Connection shard0Connection = mock(Connection.class);
    private final Connection shard1Connection = mock(Connection.class);

    private DataSourceRouter router;
    private Map<ShardingTarget, ShardingProperty> originalProperties;

    @BeforeEach
    void setUp() throws Exception {
        // ShardingPropertyMap 은 static 전역 상태 → 다른 테스트(Spring 컨텍스트)가 등록한 값을 보관했다가 복원
        originalProperties = new LinkedHashMap<>(ShardingPropertyMap.get());

        when(shard0.getConnection()).thenReturn(shard0Connection);
        when(shard1.getConnection()).thenReturn(shard1Connection);

        // NotificationDataSourceConfig 와 같은 "{shardNo} {name}" 키 형식
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("0 notification-shard-0", shard0);
        targets.put("1 notification-shard-1", shard1);

        router = new DataSourceRouter();
        router.setTargetDataSources(targets);
        router.afterPropertiesSet();

        ShardingPropertyMap.get().put(ShardingTarget.NOTIFICATION, modular(2));
    }

    @AfterEach
    void tearDown() {
        UserHolder.clearSharding();
        ShardingPropertyMap.get().clear();
        ShardingPropertyMap.get().putAll(originalProperties);
    }

    private static ShardingProperty modular(int mod) {
        ShardingProperty property = new ShardingProperty();
        property.setStrategy(ShardingStrategy.MODULAR);
        property.setMod(mod);
        return property;
    }

    @ParameterizedTest(name = "샤드키 {0} → 샤드 {1}")
    @CsvSource({"0, 0", "1, 1", "2, 0", "3, 1", "1000000000001, 1"})
    @DisplayName("MODULAR 전략은 샤드키 % mod 번호의 샤드로 라우팅한다")
    void routeByModular(long shardKey, int expectedShard) throws Exception {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, shardKey);

        Connection connection = router.getConnection();

        assertThat(connection).isSameAs(expectedShard == 0 ? shard0Connection : shard1Connection);
    }

    @Test
    @DisplayName("샤딩 정보 없이 DB 에 접근하면 예외가 발생한다")
    void noSharding() {
        assertThatThrownBy(() -> router.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("샤딩 정보 없이 DB 접근");
    }

    @Test
    @DisplayName("mod 결과에 해당하는 샤드가 없으면 예외가 발생한다 (mod 가 샤드 수보다 큰 설정 오류)")
    void shardNotExists() {
        ShardingPropertyMap.get().put(ShardingTarget.NOTIFICATION, modular(3));
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 2L); // 2 % 3 = 2 → 샤드 2 없음

        assertThatThrownBy(() -> router.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("존재하지 않는 샤드 : 2");
    }

    @Test
    @DisplayName("샤딩 설정이 등록되지 않은 대상이면 예외가 발생한다")
    void propertyNotRegistered() {
        ShardingPropertyMap.get().clear();
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 1L);

        assertThatThrownBy(() -> router.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지원하지 않는 샤딩 설정 : NOTIFICATION");
    }

    @Test
    @DisplayName("전략이 지정되지 않으면 예외가 발생한다")
    void strategyNotSet() {
        ShardingProperty property = new ShardingProperty();
        property.setMod(2);
        ShardingPropertyMap.get().put(ShardingTarget.NOTIFICATION, property);
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, 1L);

        assertThatThrownBy(() -> router.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지원하지 않는 샤딩 전략");
    }

    @Test
    @DisplayName("[현재 동작] 음수 샤드키는 음수 샤드 번호가 되어 예외가 발생한다 (id 는 항상 양수라는 전제)")
    void negativeShardKey() {
        UserHolder.setSharding(ShardingTarget.NOTIFICATION, -1L); // -1 % 2 = -1

        assertThatThrownBy(() -> router.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("존재하지 않는 샤드 : -1");
    }

    @Test
    @DisplayName("DataSource 키가 \"{shardNo} {name}\" 형식이 아니면 등록 시점에 실패한다")
    void invalidTargetKey() {
        DataSourceRouter invalid = new DataSourceRouter();

        assertThatThrownBy(() -> invalid.setTargetDataSources(Map.of("notification-shard-0", shard0)))
                .isInstanceOf(NumberFormatException.class);
    }
}
