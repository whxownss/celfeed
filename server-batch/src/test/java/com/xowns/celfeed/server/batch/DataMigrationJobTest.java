package com.xowns.celfeed.server.batch;

import com.xowns.celfeed.infra.sharding.holder.UserHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 이관 잡(dataMigrationJob) 통합 테스트
 * 알림 샤드 2대 + 배치 DB 1대를 운영과 같은 스키마(docker/mysql/{db}/init.sql)로 띄우고 잡을 실제로 실행한다.
 * <p>
 * @SpringBatchTest 는 쓰지 않는다 : 함께 등록되는 JobScopeTestExecutionListener 가 반환 타입이 JobExecution 인 메서드를
 * 찾아 테스트 전에 자동 호출하므로(잡이 한 번 더 실행됨), 스케줄러(JobScheduleConfig)처럼 JobOperator 로 직접 실행한다.
 */
@SpringBootTest
class DataMigrationJobTest {

    static final MySQLContainer SHARD_0 = mysql("celfeed_notification1", "notification1");
    static final MySQLContainer SHARD_1 = mysql("celfeed_notification2", "notification2");
    static final MySQLContainer BATCH = mysql("celfeed_batch", "batch");

    static {
        Startables.deepStart(SHARD_0, SHARD_1, BATCH).join();
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
        // Spring Boot 는 리스트를 소스끼리 합치지 않으므로 샤드 항목 전체를 등록
        registerShard(registry, 0, "notification-shard-0", SHARD_0);
        registerShard(registry, 1, "notification-shard-1", SHARD_1);

        registry.add("spring.datasource.batch.jdbc-url", BATCH::getJdbcUrl);
        registry.add("spring.datasource.batch.username", BATCH::getUsername);
        registry.add("spring.datasource.batch.password", BATCH::getPassword);
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
    JobOperator jobOperator;

    @Autowired
    Job dataMigrationJob;

    private static final String DATE = "20261001"; // 이 날짜 이전에 생성된 알림이 이관 대상
    private static final LocalDateTime BEFORE_DATE = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final LocalDateTime AFTER_DATE = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static final AtomicLong ID = new AtomicLong(1_000);

    // ========================================================================================
    // 헬퍼
    // ========================================================================================

    private static Connection connect(MySQLContainer db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    private static long insert(MySQLContainer shard, LocalDateTime createdAt) throws SQLException {
        long id = ID.incrementAndGet();
        try (Connection conn = connect(shard);
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO notification(id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at)
                     VALUES (?, ?, ?, 2, 'LIKE_POST', 500, 'N', ?)
                     """)) {
            ps.setLong(1, id);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setLong(3, shard == SHARD_0 ? 10L : 11L);
            ps.setTimestamp(4, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
        }
        return id;
    }

    private static List<Long> idsIn(MySQLContainer db) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = connect(db);
             ResultSet rs = conn.createStatement().executeQuery("SELECT id FROM notification ORDER BY id")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static void truncate(MySQLContainer db) throws SQLException {
        try (Connection conn = connect(db)) {
            conn.createStatement().executeUpdate("TRUNCATE TABLE notification");
        }
    }

    private JobExecution runJob(String date) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addString("date", date)
                .addLong("run.id", System.nanoTime()) // 실행마다 새 JobInstance
                .toJobParameters();
        return jobOperator.start(dataMigrationJob, parameters);
    }

    private static void bulkInsert(MySQLContainer shard, int count, LocalDateTime createdAt) throws SQLException {
        try (Connection conn = connect(shard);
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO notification(id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at)
                     VALUES (?, ?, 10, 2, 'WRITE_POST', 500, 'N', ?)
                     """)) {
            for (int i = 0; i < count; i++) {
                ps.setLong(1, ID.incrementAndGet());
                ps.setString(2, UUID.randomUUID().toString());
                ps.setTimestamp(3, Timestamp.valueOf(createdAt));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        truncate(SHARD_0);
        truncate(SHARD_1);
        truncate(BATCH);
    }

    // ========================================================================================

    @Test
    @DisplayName("기준일 이전에 생성된 알림을 두 샤드 모두에서 배치 DB 로 이관하고, 원본은 그대로 둔다")
    void migrateOldNotificationsFromBothShards() throws Exception {
        long old0 = insert(SHARD_0, BEFORE_DATE);
        long old1 = insert(SHARD_1, BEFORE_DATE);
        long new0 = insert(SHARD_0, AFTER_DATE);
        long new1 = insert(SHARD_1, AFTER_DATE);

        JobExecution execution = runJob(DATE);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(idsIn(BATCH)).containsExactlyInAnyOrder(old0, old1);
        assertThat(idsIn(SHARD_0)).containsExactly(old0, new0); // 원본 삭제 없음
        assertThat(idsIn(SHARD_1)).containsExactly(old1, new1);
        assertThat(UserHolder.getSharding()).isNull();           // step 이 끝나면 샤딩 정보 해제
    }

    @Test
    @DisplayName("기준일 0시 정각에 생성된 알림은 이관 대상이 아니다 (created_at < date)")
    void boundary() throws Exception {
        long justBefore = insert(SHARD_0, LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        insert(SHARD_0, LocalDateTime.of(2026, 10, 1, 0, 0, 0));

        runJob(DATE);

        assertThat(idsIn(BATCH)).containsExactly(justBefore);
    }

    @Test
    @DisplayName("알림의 모든 컬럼 값을 그대로 옮긴다")
    void copyColumns() throws Exception {
        long id = insert(SHARD_1, BEFORE_DATE);
        try (Connection conn = connect(SHARD_1)) {
            conn.createStatement().executeUpdate("UPDATE notification SET is_read = 'Y' WHERE id = " + id);
        }

        runJob(DATE);

        try (Connection source = connect(SHARD_1);
             Connection target = connect(BATCH)) {
            String sql = "SELECT id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at FROM notification WHERE id = " + id;
            ResultSet expected = source.createStatement().executeQuery(sql);
            ResultSet actual = target.createStatement().executeQuery(sql);
            assertThat(expected.next()).isTrue();
            assertThat(actual.next()).isTrue();
            for (int column = 1; column <= 8; column++) {
                assertThat(actual.getObject(column)).as("column " + column).isEqualTo(expected.getObject(column));
            }
        }
    }

    @Test
    @DisplayName("청크/페이지 크기(1000)보다 많은 알림도 빠짐없이 이관한다")
    void moreThanChunkSize() throws Exception {
        bulkInsert(SHARD_0, 2_345, BEFORE_DATE);
        bulkInsert(SHARD_1, 1_001, BEFORE_DATE);

        JobExecution execution = runJob(DATE);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(idsIn(BATCH)).hasSize(2_345 + 1_001).doesNotHaveDuplicates();
    }

    /**
     * 현재 동작 기록 : 같은 step 을 .next() 로 두 번 실행하고, 샤드를 stepExecution.getId() % 2 로 고른다.
     * 두 step 의 id 가 연속이라 지금은 샤드 0, 1 을 한 번씩 처리하지만,
     * 같은 배치 DB 를 쓰는 다른 잡/인스턴스가 동시에 step 을 실행해서 id 가 연속이 아니게 되면 한 샤드를 두 번 처리할 수 있다.
     * 샤드 번호를 step 이름이나 파라미터로 명시하는 방식을 고려.
     */
    @Test
    @DisplayName("[현재 동작] 두 step 의 id 가 연속이라 id % 2 로 샤드 0, 1 을 한 번씩 처리한다")
    void shardSelectedByStepExecutionId() throws Exception {
        insert(SHARD_0, BEFORE_DATE);
        insert(SHARD_1, BEFORE_DATE);

        JobExecution execution = runJob(DATE);

        List<Long> stepIds = execution.getStepExecutions().stream().map(StepExecution::getId).sorted().toList();
        assertThat(stepIds).hasSize(2);
        assertThat(stepIds.get(1)).isEqualTo(stepIds.get(0) + 1);
        assertThat(stepIds).extracting(id -> id % 2).containsExactlyInAnyOrder(0L, 1L);
        assertThat(execution.getStepExecutions()).allSatisfy(step ->
                assertThat(step.getWriteCount()).isEqualTo(1));
    }

    /**
     * 현재 동작 기록 (중요) : 이관 후 원본 샤드의 알림을 지우지 않고, 조건은 created_at < date 뿐이다.
     * 다음 달 실행(date 가 한 달 뒤)은 지난달에 이미 옮긴 알림까지 다시 읽어서 배치 DB 의 PK 와 충돌하고 잡이 실패한다.
     * (원본 파티션을 별도로 정리하는 운영 절차가 있다면 해당 없음)
     */
    @Test
    @DisplayName("[현재 동작] 이미 이관한 알림이 원본에 남아 있으면 다음 실행에서 PK 중복으로 잡이 실패한다")
    void rerunFailsOnAlreadyMigratedRows() throws Exception {
        insert(SHARD_0, BEFORE_DATE);                                 // 10월 실행에서 이관됨
        insert(SHARD_0, LocalDateTime.of(2026, 10, 15, 12, 0));       // 11월 실행 대상

        JobExecution october = runJob("20261001");
        JobExecution november = runJob("20261101");

        assertThat(october.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(november.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(november.getAllFailureExceptions())
                .anySatisfy(e -> assertThat(e).hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class));
    }
}
