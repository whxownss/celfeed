package com.xowns.celfeed.server.social;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.dto.request.PostRequest;
import com.xowns.celfeed.server.social.outbox.OutboxCleanupScheduler;
import com.xowns.celfeed.server.social.outbox.OutboxEventRecorder;
import com.xowns.celfeed.server.social.outbox.OutboxRelay;
import com.xowns.celfeed.server.social.service.FollowService;
import com.xowns.celfeed.server.social.service.PostService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.xowns.celfeed.server.social.support.Fixtures.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * SocialApplication 전체를 실제 인프라(MySQL + Redis + Kafka)로 띄워 Outbox 발행 흐름을 검증
 * <ul>
 *     <li>운영과 같은 이미지, 스키마(docker/mysql/social/init.sql)</li>
 *     <li>fallback relay / cleanup 스케줄러는 주기를 길게 바꾸고 필요한 테스트에서 직접 호출</li>
 *     <li>Kafka 메시지는 테스트용 consumer 로 직접 읽어서 확인</li>
 * </ul>
 */
@SpringBootTest
class OutboxIntegrationTest {

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("celfeed_social")
            .withUsername("root")
            .withPassword("root")
            .withEnv("TZ", "Asia/Seoul")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../docker/mysql/social/init.sql"), // 모듈 디렉터리 기준
                    "/docker-entrypoint-initdb.d/init.sql")
            .withTmpFs(Map.of("/var/lib/mysql", "rw")) // Windows Docker 파일 I/O 가 느려서 메모리 사용
            .withStartupTimeout(Duration.ofMinutes(5));
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");

    static {
        Startables.deepStart(MYSQL, REDIS, KAFKA).join();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.social.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.social.username", MYSQL::getUsername);
        registry.add("spring.datasource.social.password", MYSQL::getPassword);
        registry.add("spring.datasource.social.maximum-pool-size", () -> 5);
        registry.add("spring.datasource.social.minimum-idle", () -> 1);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // 스케줄러가 테스트 중간에 끼어들지 않게 (시작 시 1회 실행 후 1시간 대기)
        registry.add("app.outbox.relay.fixed-delay", () -> 3_600_000);
        registry.add("app.outbox.scheduler.fixed-delay", () -> 3_600_000);
    }

    @Autowired PostService postService;
    @Autowired FollowService followService;
    @Autowired MemberRepository memberRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired OutboxEventRecorder outboxEventRecorder;
    @Autowired OutboxRelay outboxRelay;
    @Autowired OutboxCleanupScheduler outboxCleanupScheduler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;

    // ========================================================================================
    // 헬퍼
    // ========================================================================================

    private Member member() {
        String nickname = "m" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.create(nickname, nickname + "@test.com", "password1", MemberRole.CELEB));
    }

    private static PostRequest content(String content) {
        return fromJson("{\"content\": \"%s\"}".formatted(content), PostRequest.class);
    }

    private record Outbox(String eventId, String status, String partitionKey) {
    }

    private Optional<Outbox> outbox(String eventType, long aggregateId) {
        return jdbcTemplate.query(
                "SELECT event_id, status, partition_key FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                (rs, i) -> new Outbox(rs.getString(1), rs.getString(2), rs.getString(3)),
                eventType, aggregateId).stream().findFirst();
    }

    private Outbox awaitPublished(String eventType, long aggregateId) {
        return await().atMost(Duration.ofSeconds(20)).until(
                () -> outbox(eventType, aggregateId).orElse(null),
                outbox -> outbox != null && outbox.status().equals("PUBLISHED"));
    }

    /** 토픽을 처음부터 읽어서 payload 에 eventId 가 포함된 메시지를 찾는다 */
    private ConsumerRecord<String, String> awaitMessage(String topic, String eventId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(eventId)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("메시지를 찾지 못함 : topic=" + topic + ", eventId=" + eventId);
    }

    // ========================================================================================
    // 커밋 후 즉시 발행
    // ========================================================================================

    @Test
    @DisplayName("게시글을 작성하면 커밋 후 write-post 토픽으로 발행되고 Outbox 가 PUBLISHED 가 된다")
    void writePostPublished() {
        Member writer = member();

        Long postId = postService.write(writer.getId(), content("hello celfeed"));

        Outbox outbox = awaitPublished("WRITE_POST", postId);
        ConsumerRecord<String, String> record = awaitMessage(KafkaTopic.WRITE_POST, outbox.eventId());
        assertThat(record.key()).isEqualTo(String.valueOf(postId));
        assertThat(record.value()).contains("\"postId\":" + postId).contains("\"actorId\":" + writer.getId());
    }

    @Test
    @DisplayName("좋아요는 like-post 토픽으로, 파티션 키 postId:actorId 로 발행된다")
    void likePostPublished() {
        Member writer = member();
        Member fan = member();
        Long postId = postService.write(writer.getId(), content("hello celfeed"));

        Long likeId = postService.likePost(fan.getId(), postId);

        Outbox outbox = awaitPublished("LIKE_POST", likeId);
        ConsumerRecord<String, String> record = awaitMessage(KafkaTopic.LIKE_POST, outbox.eventId());
        assertThat(record.key()).isEqualTo(postId + ":" + fan.getId());
    }

    @Test
    @DisplayName("팔로우는 follow-member 토픽으로 발행되고, consumer 가 같은 이벤트 클래스로 읽을 수 있다")
    void followMemberPublished() {
        Member fan = member();
        Member celeb = member();

        Long followId = followService.followMember(fan.getId(), celeb.getId());

        Outbox outbox = awaitPublished("FOLLOW_MEMBER", followId);
        ConsumerRecord<String, String> record = awaitMessage(KafkaTopic.FOLLOW_MEMBER, outbox.eventId());
        assertThat(record.key()).isEqualTo(celeb.getId() + ":" + fan.getId());

        FollowMemberEvent event = objectMapper.readValue(record.value(), FollowMemberEvent.class);
        assertThat(event.getFollowId()).isEqualTo(followId);
        assertThat(event.getFromId()).isEqualTo(fan.getId());
        assertThat(event.getToId()).isEqualTo(celeb.getId());
        assertThat(event.getOccurredAt()).isNotNull();
    }

    // ========================================================================================
    // 트랜잭션 경계
    // ========================================================================================

    @Test
    @DisplayName("비즈니스 트랜잭션이 롤백되면 Outbox 도 저장되지 않고 발행도 일어나지 않는다")
    void rollbackDoesNotPublish() {
        Member writer = member();
        long before = outboxRepository.count();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            postService.write(writer.getId(), content("will be rolled back"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Outbox 기록은 비즈니스 트랜잭션 안에서만 가능하다 (MANDATORY)")
    void recordRequiresTransaction() {
        assertThatThrownBy(() -> outboxEventRecorder.record(new FollowMemberEvent(1L, 1L, 2L)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ========================================================================================
    // fallback relay / cleanup
    // ========================================================================================

    @Test
    @DisplayName("즉시 발행되지 못하고 PENDING 으로 남은 이벤트는 fallback relay 가 발행하고 PUBLISHED 로 바꾼다")
    void fallbackRelayPublishesPending() {
        // 즉시 발행이 실패해서 PENDING 으로 남은 상황 (OutboxCreatedEvent 없이 직접 저장)
        String eventId = UUID.randomUUID().toString();
        long aggregateId = Math.abs(new Random().nextLong() % 1_000_000_000L);
        String payload = "{\"eventId\":\"" + eventId + "\",\"postId\":" + aggregateId + ",\"actorId\":1}";
        outboxRepository.save(OutboxEvent.pending(eventId, "WRITE_POST", aggregateId, String.valueOf(aggregateId), payload));

        outboxRelay.relayFallback();

        assertThat(outbox("WRITE_POST", aggregateId)).get().extracting(Outbox::status).isEqualTo("PUBLISHED");
        assertThat(awaitMessage(KafkaTopic.WRITE_POST, eventId).value()).isEqualTo(payload);
    }

    @Test
    @DisplayName("cleanup 은 TTL 이 지난 PUBLISHED 만 삭제하고, 최근 이벤트와 PENDING 은 남긴다")
    void cleanupDeletesOnlyOldPublished() {
        long oldPublished = saveOutbox("PUBLISHED", LocalDateTime.now().minusHours(7));
        long recentPublished = saveOutbox("PUBLISHED", LocalDateTime.now().minusHours(1));
        long oldPending = saveOutbox("PENDING", LocalDateTime.now().minusHours(7));

        outboxCleanupScheduler.cleanup(); // ttl-hours = 6

        assertThat(outboxRepository.findById(oldPublished)).isEmpty();
        assertThat(outboxRepository.findById(recentPublished)).isPresent();
        assertThat(outboxRepository.findById(oldPending)).isPresent();
    }

    private long saveOutbox(String status, LocalDateTime createdAt) {
        String eventId = UUID.randomUUID().toString();
        OutboxEvent saved = outboxRepository.save(OutboxEvent.pending(eventId, "WRITE_POST", 1L, "1", "{}"));
        jdbcTemplate.update("UPDATE outbox_event SET status = ?, created_at = ?, retry_at = ? WHERE id = ?",
                status, createdAt, LocalDateTime.now().plusHours(1), saved.getId()); // retry_at 미래 → fallback 대상 제외
        return saved.getId();
    }
}
