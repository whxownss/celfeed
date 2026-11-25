package com.xowns.celfeed.infra.sharding.integration;

import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 MySQL 샤드 2개(운영처럼 서버 2대)로 NotificationJpaConfig 전체를 띄워 라우팅을 검증
 * <ul>
 *     <li>샤드 0 = celfeed_notification1, 샤드 1 = celfeed_notification2 (운영 init.sql 그대로 사용)</li>
 *     <li>컨테이너는 JVM 당 한 번만 띄우는 싱글톤</li>
 *     <li>저장 결과는 Spring 을 거치지 않고 각 샤드 DB 에 직접 접속해서 확인</li>
 * </ul>
 */
@SpringBootTest(classes = ShardingTestApplication.class)
class ShardingIntegrationTest {

    static final MySQLContainer SHARD_0 = shard("celfeed_notification1", "notification1");
    static final MySQLContainer SHARD_1 = shard("celfeed_notification2", "notification2");

    static {
        Startables.deepStart(SHARD_0, SHARD_1).join();
    }

    private static MySQLContainer shard(String database, String initDir) {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName(database)
                .withUsername("root")
                .withPassword("root")
                .withEnv("TZ", "Asia/Seoul")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../docker/mysql/" + initDir + "/init.sql"), // 모듈 디렉터리 기준
                        "/docker-entrypoint-initdb.d/init.sql"
                )
                // 데이터 디렉터리를 메모리에 두어 초기화 속도 개선 (Windows Docker 파일 I/O 가 느림)
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                // 2대를 동시에 초기화하면 기본 대기 시간(2분)을 넘길 수 있음
                .withStartupTimeout(Duration.ofMinutes(5));
    }

    /** Spring Boot 는 리스트를 소스끼리 합치지 않으므로 샤드 항목 전체를 등록 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerShard(registry, 0, "notification-shard-0", SHARD_0);
        registerShard(registry, 1, "notification-shard-1", SHARD_1);
        registry.add("app.sharding.notification.strategy", () -> "MODULAR");
        registry.add("app.sharding.notification.mod", () -> 2);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    private static void registerShard(DynamicPropertyRegistry registry, int shardNo, String name, MySQLContainer db) {
        String prefix = "app.datasource.notification.shards[" + shardNo + "]";
        registry.add(prefix + ".shard-no", () -> shardNo);
        registry.add(prefix + ".name", () -> name);
        registry.add(prefix + ".url", () -> db.getJdbcUrl() + "?rewriteBatchedStatements=true");
        registry.add(prefix + ".username", db::getUsername);
        registry.add(prefix + ".password", db::getPassword);
        registry.add(prefix + ".driver-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add(prefix + ".max-pool", () -> 3);
        registry.add(prefix + ".min-idle", () -> 1);
        registry.add(prefix + ".timeout", () -> 3000);
    }

    @Autowired
    ShardedNotificationService service;

    @Autowired
    NotificationRepository notificationRepository;

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    /** 각 샤드 DB 에 직접 접속해서 eventId 로 저장된 수신자 목록을 조회 */
    private static List<Long> receiversIn(MySQLContainer shard, String eventId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(shard.getJdbcUrl(), shard.getUsername(), shard.getPassword());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT receiver_id FROM notification WHERE event_id = ? ORDER BY receiver_id")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> receivers = new ArrayList<>();
                while (rs.next()) {
                    receivers.add(rs.getLong(1));
                }
                return receivers;
            }
        }
    }

    // ========================================================================================

    @Test
    @DisplayName("애플리케이션 시작 시 샤딩 정보가 없어도 컨텍스트가 정상적으로 뜬다 (부팅 중 DB 커넥션을 요구하지 않음)")
    void contextLoads() {
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("JPA 저장은 샤드키(수신자 id) % 2 번 샤드에만 저장된다")
    void jpaSaveRoutesToShard() throws SQLException {
        String eventId = newEventId();

        service.save(10L, eventId); // 10 % 2 = 0
        service.save(11L, eventId); // 11 % 2 = 1

        assertThat(receiversIn(SHARD_0, eventId)).containsExactly(10L);
        assertThat(receiversIn(SHARD_1, eventId)).containsExactly(11L);
    }

    @Test
    @DisplayName("저장한 샤드키로 조회하면 찾을 수 있고, 다른 샤드키로 조회하면 찾을 수 없다")
    void findOnlyInSameShard() {
        Notification saved = service.save(20L, newEventId()); // 샤드 0

        assertThat(service.find(20L, saved.getId())).isPresent();
        assertThat(service.find(21L, saved.getId())).isEmpty(); // 샤드 1 에는 없음
    }

    @Test
    @DisplayName("JdbcTemplate bulk insert 도 같은 라우팅을 따라 샤드키의 샤드에 저장된다")
    void bulkInsertRoutesToShard() throws SQLException {
        String eventId = newEventId();

        service.bulkSave(1L, List.of(1L, 3L, 5L), eventId); // 샤드 1

        assertThat(receiversIn(SHARD_1, eventId)).containsExactly(1L, 3L, 5L);
        assertThat(receiversIn(SHARD_0, eventId)).isEmpty();
    }

    @Test
    @DisplayName("예외가 발생하면 해당 샤드의 트랜잭션이 롤백된다")
    void rollbackOnException() throws SQLException {
        String eventId = newEventId();

        assertThatThrownBy(() -> service.saveThenFail(30L, eventId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(receiversIn(SHARD_0, eventId)).isEmpty();
    }

    @Test
    @DisplayName("@Sharding 서비스를 거치지 않고 리포지토리로 바로 접근하면 샤딩 정보가 없어 실패한다")
    void accessWithoutShardingFails() {
        assertThatThrownBy(() -> notificationRepository.findById(1L))
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("샤딩 정보 없이 DB 접근"));
    }
}
