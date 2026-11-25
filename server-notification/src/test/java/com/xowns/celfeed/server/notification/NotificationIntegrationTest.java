package com.xowns.celfeed.server.notification;

import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.server.notification.dto.NotificationResponse;
import com.xowns.celfeed.server.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationApplication 전체를 실제 인프라(소셜 DB 1대 + 알림 샤드 2대 + Redis)로 띄워 검증
 * <ul>
 *     <li>운영과 같은 이미지, 타임존, 스키마(docker/mysql/{db}/init.sql)</li>
 *     <li>회원은 소셜 DB 에, 알림은 각 샤드 DB 에 직접 넣고/확인해서 샤딩 라우팅을 검증</li>
 *     <li>컨테이너는 JVM 당 한 번만 띄우는 싱글톤</li>
 * </ul>
 */
@SpringBootTest
class NotificationIntegrationTest {

    static final MySQLContainer SOCIAL = mysql("celfeed_social", "social");
    static final MySQLContainer SHARD_0 = mysql("celfeed_notification1", "notification1");
    static final MySQLContainer SHARD_1 = mysql("celfeed_notification2", "notification2");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        Startables.deepStart(SOCIAL, SHARD_0, SHARD_1, REDIS).join();
    }

    private static MySQLContainer mysql(String database, String initDir) {
        return new MySQLContainer("mysql:8.4")
                .withDatabaseName(database)
                .withUsername("root")
                .withPassword("root")
                .withEnv("TZ", "Asia/Seoul")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../docker/mysql/" + initDir + "/init.sql"), // 모듈 디렉터리 기준
                        "/docker-entrypoint-initdb.d/init.sql")
                .withTmpFs(Map.of("/var/lib/mysql", "rw")) // Windows Docker 파일 I/O 가 느려서 메모리 사용
                .withStartupTimeout(Duration.ofMinutes(5));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.social.jdbc-url", SOCIAL::getJdbcUrl);
        registry.add("spring.datasource.social.username", SOCIAL::getUsername);
        registry.add("spring.datasource.social.password", SOCIAL::getPassword);
        registry.add("spring.datasource.social.maximum-pool-size", () -> 3);
        registry.add("spring.datasource.social.minimum-idle", () -> 1);

        // Spring Boot 는 리스트를 소스끼리 합치지 않으므로 샤드 항목 전체를 등록
        registerShard(registry, 0, "notification-shard-0", SHARD_0);
        registerShard(registry, 1, "notification-shard-1", SHARD_1);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
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
    NotificationService notificationService;

    @Autowired
    MemberRepository memberRepository;

    private static final AtomicLong NOTIFICATION_ID = new AtomicLong(System.currentTimeMillis());

    // ========================================================================================
    // 테스트 데이터 헬퍼 (각 DB 에 직접 접근)
    // ========================================================================================

    private Member member() {
        String nickname = "m" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.create(nickname, nickname + "@test.com", "pw", MemberRole.FAN));
    }

    private static MySQLContainer shardOf(long receiverId) {
        return receiverId % 2 == 0 ? SHARD_0 : SHARD_1;
    }

    private static MySQLContainer otherShardOf(long receiverId) {
        return receiverId % 2 == 0 ? SHARD_1 : SHARD_0;
    }

    private static Connection connect(MySQLContainer db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    private static long insertNotification(MySQLContainer shard, long receiverId, long actorId, LocalDateTime createdAt)
            throws SQLException {
        long id = NOTIFICATION_ID.incrementAndGet();
        try (Connection conn = connect(shard);
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO notification(id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at)
                     VALUES (?, ?, ?, ?, 'LIKE_POST', 500, 'N', ?)
                     """)) {
            ps.setLong(1, id);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setLong(3, receiverId);
            ps.setLong(4, actorId);
            ps.setTimestamp(5, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
        }
        return id;
    }

    /** 알림 행의 is_read 값, 행이 없으면 null */
    private static String isRead(MySQLContainer shard, long notificationId) throws SQLException {
        try (Connection conn = connect(shard);
             PreparedStatement ps = conn.prepareStatement("SELECT is_read FROM notification WHERE id = ?")) {
            ps.setLong(1, notificationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ========================================================================================

    @Test
    @DisplayName("알림 목록은 로그인 회원의 샤드에서, 본인 알림만 최신순으로, actor 닉네임과 함께 조회된다")
    void findAllFromOwnShard() throws SQLException {
        Member me = member();
        Member actor1 = member();
        Member actor2 = member();
        Member other = member();
        LocalDateTime now = LocalDateTime.now();

        long older = insertNotification(shardOf(me.getId()), me.getId(), actor1.getId(), now.minusDays(2));
        long newer = insertNotification(shardOf(me.getId()), me.getId(), actor2.getId(), now.minusHours(1));
        insertNotification(shardOf(other.getId()), other.getId(), actor1.getId(), now.minusHours(1));  // 다른 회원
        insertNotification(otherShardOf(me.getId()), me.getId(), actor1.getId(), now.minusHours(1));   // 다른 샤드에 잘못 저장된 데이터

        SliceDTO<NotificationResponse> result = notificationService.findAll(me.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(NotificationResponse::getId).containsExactly(newer, older);
        assertThat(result.getContent()).extracting(NotificationResponse::getMessage).containsExactly(
                "[" + actor2.getNickname() + "]님이 내 게시글에 좋아요를 눌렀습니다.",
                "[" + actor1.getNickname() + "]님이 내 게시글에 좋아요를 눌렀습니다.");
    }

    @Test
    @DisplayName("29일 전 0시에 생성된 알림까지 조회되고, 그 이전 알림은 조회되지 않는다")
    void periodBoundary() throws SQLException {
        Member me = member();
        Member actor = member();
        LocalDateTime from = LocalDate.now().minusDays(29).atStartOfDay();

        long included = insertNotification(shardOf(me.getId()), me.getId(), actor.getId(), from);
        insertNotification(shardOf(me.getId()), me.getId(), actor.getId(), from.minusSeconds(1));

        SliceDTO<NotificationResponse> result = notificationService.findAll(me.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(NotificationResponse::getId).containsExactly(included);
    }

    @Test
    @DisplayName("본인 알림을 읽음 처리하면 해당 샤드 DB 에 Y 로 저장된다")
    void readOwnNotification() throws SQLException {
        Member me = member();
        long id = insertNotification(shardOf(me.getId()), me.getId(), member().getId(), LocalDateTime.now());

        notificationService.readNotification(me.getId(), id);

        assertThat(isRead(shardOf(me.getId()), id)).isEqualTo("Y");
    }

    @Test
    @DisplayName("다른 회원의 알림은 읽음 처리되지 않는다")
    void readOthersNotification() throws SQLException {
        Member me = member();
        Member other = member();
        // 같은 샤드에 있는 다른 회원의 알림을 대상으로 시도
        long othersId = insertNotification(shardOf(me.getId()), other.getId(), member().getId(), LocalDateTime.now());

        notificationService.readNotification(me.getId(), othersId);

        assertThat(isRead(shardOf(me.getId()), othersId)).isEqualTo("N");
    }

    @Test
    @DisplayName("본인 알림을 삭제하면 해당 샤드 DB 에서 삭제되고, 다른 회원의 알림은 삭제되지 않는다")
    void deleteNotification() throws SQLException {
        Member me = member();
        Member other = member();
        long mine = insertNotification(shardOf(me.getId()), me.getId(), member().getId(), LocalDateTime.now());
        long others = insertNotification(shardOf(me.getId()), other.getId(), member().getId(), LocalDateTime.now());

        notificationService.deleteNotification(me.getId(), mine);
        notificationService.deleteNotification(me.getId(), others);

        assertThat(isRead(shardOf(me.getId()), mine)).isNull();      // 삭제됨
        assertThat(isRead(shardOf(me.getId()), others)).isEqualTo("N"); // 그대로
    }
}
