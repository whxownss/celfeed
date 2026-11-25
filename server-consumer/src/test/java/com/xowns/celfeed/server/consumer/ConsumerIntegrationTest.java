package com.xowns.celfeed.server.consumer;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.domain.social.entity.*;
import com.xowns.celfeed.domain.social.repository.FollowRepository;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * ConsumerApplication 전체를 실제 인프라(소셜 DB + 알림 샤드 2대 + Redis + Kafka)로 띄워 검증
 * <ul>
 *     <li>운영과 같은 이미지, 스키마(docker/mysql/{db}/init.sql)</li>
 *     <li>테스트 producer 로 이벤트를 보내고, 각 샤드 DB 에 직접 접속해서 알림 저장을 확인</li>
 *     <li>재시도 간격/락 TTL 은 짧게, write-post batch-size 는 2 로 바꿔서 fanout 구간이 여러 개 생기게 함</li>
 * </ul>
 */
@SpringBootTest
class ConsumerIntegrationTest {

    static final MySQLContainer SOCIAL = mysql("celfeed_social", "social");
    static final MySQLContainer SHARD_0 = mysql("celfeed_notification1", "notification1");
    static final MySQLContainer SHARD_1 = mysql("celfeed_notification2", "notification2");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");

    static final int BATCH_SIZE = 2;

    static {
        Startables.deepStart(SOCIAL, SHARD_0, SHARD_1, REDIS, KAFKA).join();
        createSourceTopics();
        warmUpTransactionCoordinator();
    }

    /**
     * 새로 띄운 브로커는 첫 트랜잭션 초기화(__transaction_state 토픽 생성)에 수 초가 걸려서,
     * 운영 설정 max.block.ms(3초) 안에 끝나지 않으면 consumer 의 첫 write-post 처리가 initTransactions 실패로 지연된다.
     * 테스트 시작 전에 한 번 초기화해 둔다. (테스트 환경 문제라 운영 설정은 건드리지 않음)
     */
    private static void warmUpTransactionCoordinator() {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.TRANSACTIONAL_ID_CONFIG, "warm-up",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 120_000,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.initTransactions();
        }
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

    /** like-post / write-post / follow-member 토픽은 운영에서 server-social 이 만들므로 테스트에서 미리 생성 */
    private static void createSourceTopics() {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(KafkaTopic.LIKE_POST, 1, (short) 1),
                    new NewTopic(KafkaTopic.WRITE_POST, 1, (short) 1),
                    new NewTopic(KafkaTopic.FOLLOW_MEMBER, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.social.jdbc-url", SOCIAL::getJdbcUrl);
        registry.add("spring.datasource.social.username", SOCIAL::getUsername);
        registry.add("spring.datasource.social.password", SOCIAL::getPassword);
        registry.add("spring.datasource.social.maximum-pool-size", () -> 5);
        registry.add("spring.datasource.social.minimum-idle", () -> 1);

        // Spring Boot 는 리스트를 소스끼리 합치지 않으므로 샤드 항목 전체를 등록
        registerShard(registry, 0, "notification-shard-0", SHARD_0);
        registerShard(registry, 1, "notification-shard-1", SHARD_1);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // 운영과 같은 규칙 유지 : 재시도 총 대기(0.2 + 0.4 + 0.8 + 1.0 = 2.4초) > 락 TTL(2초)
        registry.add("app.kafka.consumer.lock-ttl-seconds", () -> 2);
        registry.add("app.kafka.consumer.retry.max-retries", () -> 4);
        registry.add("app.kafka.consumer.retry.initial-interval", () -> "200ms");
        registry.add("app.kafka.consumer.retry.multiplier", () -> 2.0);
        registry.add("app.kafka.consumer.retry.max-interval", () -> "1s");
        registry.add("app.kafka.consumers.write-post.batch-size", () -> BATCH_SIZE);
        for (String consumer : List.of("like-post", "write-post-fanout", "follow-member")) {
            registry.add("app.kafka.consumers." + consumer + ".concurrency", () -> 1);
        }
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

    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired LikeRepository likeRepository;
    @Autowired FollowRepository followRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper objectMapper;

    private static final KafkaProducer<String, String> PRODUCER = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));

    @AfterAll
    static void closeProducer() {
        PRODUCER.close();
    }

    // ========================================================================================
    // 헬퍼
    // ========================================================================================

    private Member member() {
        String nickname = "m" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.create(nickname, nickname + "@test.com", "pw", MemberRole.CELEB));
    }

    private void send(String topic, String key, Object event) {
        String payload = event instanceof String s ? s : objectMapper.writeValueAsString(event);
        try {
            PRODUCER.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MySQLContainer shardOf(long receiverId) {
        return receiverId % 2 == 0 ? SHARD_0 : SHARD_1;
    }

    /** 이벤트로 저장된 알림의 수신자 목록 (두 샤드 모두 조회) */
    private static List<Long> receiversOf(String eventId) {
        List<Long> receivers = new ArrayList<>();
        for (MySQLContainer shard : List.of(SHARD_0, SHARD_1)) {
            try (Connection conn = DriverManager.getConnection(shard.getJdbcUrl(), shard.getUsername(), shard.getPassword());
                 PreparedStatement ps = conn.prepareStatement("SELECT receiver_id FROM notification WHERE event_id = ?")) {
                ps.setString(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        receivers.add(rs.getLong(1));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        Collections.sort(receivers);
        return receivers;
    }

    private static long countIn(MySQLContainer shard, String eventId) {
        try (Connection conn = DriverManager.getConnection(shard.getJdbcUrl(), shard.getUsername(), shard.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM notification WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 토픽을 처음부터 읽어 조건에 맞는 메시지들을 모은다 (최대 timeout 동안, 기대 개수가 모이면 종료) */
    private static List<String> readMessages(String topic, String contains, int expected, Duration timeout) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && found.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
                    if (record.value() != null && record.value().contains(contains)) {
                        found.add(record.value());
                    }
                }
            }
        }
        return found;
    }

    private static void awaitReceivers(String eventId, List<Long> expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(receiversOf(eventId)).containsExactlyElementsOf(expected));
    }

    // ========================================================================================
    // like-post / follow-member
    // ========================================================================================

    @Nested
    @DisplayName("like-post / follow-member")
    class LikeAndFollow {

        @Test
        @DisplayName("좋아요 이벤트를 받으면 게시글 작성자의 샤드에 알림 1건을 저장한다")
        void likePost() {
            Member writer = member();
            Member fan = member();
            Post post = postRepository.save(Post.create(writer, "content"));
            Like like = likeRepository.save(Like.create(post, fan));
            LikePostEvent event = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());

            send(KafkaTopic.LIKE_POST, post.getId() + ":" + fan.getId(), event);

            awaitReceivers(event.getEventId(), List.of(writer.getId()));
            assertThat(countIn(shardOf(writer.getId()), event.getEventId())).isEqualTo(1);
        }

        @Test
        @DisplayName("팔로우 이벤트를 받으면 팔로우 당한 회원의 샤드에 알림 1건을 저장한다")
        void followMember() {
            Member fan = member();
            Member celeb = member();
            Follow follow = followRepository.save(Follow.create(fan, celeb));
            FollowMemberEvent event = new FollowMemberEvent(follow.getId(), fan.getId(), celeb.getId());

            send(KafkaTopic.FOLLOW_MEMBER, celeb.getId() + ":" + fan.getId(), event);

            awaitReceivers(event.getEventId(), List.of(celeb.getId()));
        }

        @Test
        @DisplayName("같은 이벤트가 두 번 오면(Producer 중복 발행) Redis 완료 마크로 1건만 저장한다")
        void duplicateMessage() {
            Member writer = member();
            Member fan = member();
            Post post = postRepository.save(Post.create(writer, "content"));
            Like like = likeRepository.save(Like.create(post, fan));
            LikePostEvent event = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());
            String key = post.getId() + ":" + fan.getId();

            send(KafkaTopic.LIKE_POST, key, event);
            send(KafkaTopic.LIKE_POST, key, event);
            // 같은 파티션의 다음 메시지가 처리됐으면 앞의 중복 메시지도 처리 끝난 것
            LikePostEvent marker = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());
            send(KafkaTopic.LIKE_POST, key, marker);

            awaitReceivers(marker.getEventId(), List.of(writer.getId()));
            assertThat(receiversOf(event.getEventId())).containsExactly(writer.getId());
        }

        /**
         * 현재 동작 기록 (중요) : domain-notification 테스트에서 확인한 유니크 키 문제가 실제 흐름에서도 재현된다.
         * Redis 완료 마크가 사라진 상태(Redis 장애 / TTL 만료)에서 같은 이벤트를 다시 받으면,
         * 유니크 키 (event_id, receiver_id, created_at) 의 created_at 이 달라서 중복 저장된다.
         */
        @Test
        @DisplayName("[현재 동작] Redis 완료 마크가 사라진 뒤 같은 이벤트를 다시 받으면 알림이 중복 저장된다")
        void duplicateAfterRedisMarkLost() {
            Member writer = member();
            Member fan = member();
            Post post = postRepository.save(Post.create(writer, "content"));
            Like like = likeRepository.save(Like.create(post, fan));
            LikePostEvent event = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());
            String key = post.getId() + ":" + fan.getId();

            send(KafkaTopic.LIKE_POST, key, event);
            awaitReceivers(event.getEventId(), List.of(writer.getId()));

            redisTemplate.delete("idempotency:done:like-post:" + event.getEventId()); // TTL 만료 / Redis 유실
            send(KafkaTopic.LIKE_POST, key, event);

            awaitReceivers(event.getEventId(), List.of(writer.getId(), writer.getId()));
        }

        @Test
        @DisplayName("다른 consumer 가 락을 잡고 있으면 재시도하다가, 락이 만료된 뒤 처리한다 (DLT 로 보내지 않음)")
        void retryUntilLockExpires() {
            Member writer = member();
            Member fan = member();
            Post post = postRepository.save(Post.create(writer, "content"));
            Like like = likeRepository.save(Like.create(post, fan));
            LikePostEvent event = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());
            // 처리 중 죽은 consumer 의 락 (TTL 1.5초)
            redisTemplate.opsForValue().set("idempotency:lock:like-post:" + event.getEventId(), "dead-consumer", Duration.ofMillis(1500));

            send(KafkaTopic.LIKE_POST, post.getId() + ":" + fan.getId(), event);

            awaitReceivers(event.getEventId(), List.of(writer.getId()));
            assertThat(readMessages(KafkaTopic.LIKE_POST_DLT, event.getEventId(), 1, Duration.ofSeconds(2))).isEmpty();
        }

        @Test
        @DisplayName("역직렬화할 수 없는 메시지는 재시도 없이 DLT 로 보낸다")
        void invalidMessageToDlt() {
            String marker = "broken-" + UUID.randomUUID();

            send(KafkaTopic.LIKE_POST, "k", "{ " + marker);

            assertThat(readMessages(KafkaTopic.LIKE_POST_DLT, marker, 1, Duration.ofSeconds(20))).hasSize(1);
        }

        @Test
        @DisplayName("좋아요가 이미 취소된 이벤트는 알림을 저장하지 않고 DLT 로도 보내지 않는다")
        void likeAlreadyCancelled() {
            Member writer = member();
            Member fan = member();
            Post post = postRepository.save(Post.create(writer, "content"));
            LikePostEvent cancelled = new LikePostEvent(Long.MAX_VALUE, post.getId(), writer.getId(), fan.getId());
            String key = post.getId() + ":" + fan.getId();

            send(KafkaTopic.LIKE_POST, key, cancelled);
            Like like = likeRepository.save(Like.create(post, fan));
            LikePostEvent marker = new LikePostEvent(like.getId(), post.getId(), writer.getId(), fan.getId());
            send(KafkaTopic.LIKE_POST, key, marker);

            awaitReceivers(marker.getEventId(), List.of(writer.getId()));
            assertThat(receiversOf(cancelled.getEventId())).isEmpty();
            assertThat(readMessages(KafkaTopic.LIKE_POST_DLT, cancelled.getEventId(), 1, Duration.ofSeconds(2))).isEmpty();
        }
    }

    // ========================================================================================
    // write-post → write-post-fanout
    // ========================================================================================

    @Nested
    @DisplayName("write-post → write-post-fanout")
    class WritePostFanout {

        private List<Long> followers(Member celeb, int count) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Member fan = member();
                followRepository.save(Follow.create(fan, celeb));
                ids.add(fan.getId());
            }
            return ids;
        }

        /**
         * 현재 동작 기록 (중요 — 버그)
         * WritePostListener#markProcessedAfterCommit 은 TransactionSynchronizationManager.registerSynchronization() 을 호출하는데,
         * 이 컨테이너가 쓰는 KafkaTransactionManager 는 기본값이 SYNCHRONIZATION_NEVER 라 트랜잭션 동기화가 활성화되지 않는다.
         * → 매번 "Transaction synchronization is not active" 예외 → Kafka 트랜잭션 롤백(보낸 fanout 메시지도 abort)
         * → 재시도를 다 쓰고 write-post.DLT 로 이동 → 게시글 알림이 하나도 저장되지 않는다.
         * (수정 방향 예 : kafkaTransactionManager.setTransactionSynchronization(SYNCHRONIZATION_ALWAYS))
         */
        @Test
        @DisplayName("[현재 동작] 게시글 이벤트는 트랜잭션 동기화가 비활성이라 매번 롤백되어 write-post.DLT 로 가고, 알림이 저장되지 않는다")
        void writePostAlwaysRollsBack() {
            Member celeb = member();
            followers(celeb, 5);
            Post post = postRepository.save(Post.create(celeb, "new post"));
            WritePostEvent event = new WritePostEvent(post.getId(), celeb.getId());

            send(KafkaTopic.WRITE_POST, String.valueOf(post.getId()), event);

            assertThat(readMessages(KafkaTopic.WRITE_POST_DLT, event.getEventId(), 1, Duration.ofSeconds(40))).hasSize(1);
            // 롤백된(abort) fanout 메시지는 read_committed 로 읽히지 않음
            assertThat(readMessages(KafkaTopic.WRITE_POST_FANOUT, event.getEventId(), 1, Duration.ofSeconds(3))).isEmpty();
            assertThat(receiversOf(event.getEventId())).isEmpty();
        }

        @Test
        @DisplayName("fanout 중간 구간 메시지를 받으면 구간 (start, end] 팔로워들의 샤드에 알림을 저장한다")
        void fanoutMiddleSegment() {
            Member celeb = member();
            List<Long> ids = followers(celeb, 4);
            String eventId = UUID.randomUUID().toString();
            Post post = postRepository.save(Post.create(celeb, "new post"));

            send(KafkaTopic.WRITE_POST_FANOUT, eventId + ":" + ids.get(0), new com.xowns.celfeed.server.consumer.listener.event.WritePostFanoutEvent(
                    eventId, post.getId(), celeb.getId(), celeb.getNickname(), ids.get(0), ids.get(3)));

            awaitReceivers(eventId, List.of(ids.get(1), ids.get(2), ids.get(3)));
        }

        /** 현재 동작 기록 (중요 — 버그로 보임) : 리스너 단위 테스트 WritePostFanoutListenerTest 와 같은 내용을 실제 흐름에서 확인 */
        @Test
        @DisplayName("[현재 동작] 첫 구간(start = null) fanout 메시지는 필수 값 누락으로 DLT 로 가고 알림이 저장되지 않는다")
        void fanoutFirstSegmentRejected() {
            Member celeb = member();
            List<Long> ids = followers(celeb, 2);
            String eventId = UUID.randomUUID().toString();
            Post post = postRepository.save(Post.create(celeb, "new post"));

            send(KafkaTopic.WRITE_POST_FANOUT, eventId + ":null", new com.xowns.celfeed.server.consumer.listener.event.WritePostFanoutEvent(
                    eventId, post.getId(), celeb.getId(), celeb.getNickname(), null, ids.get(1)));

            assertThat(readMessages(KafkaTopic.WRITE_POST_FANOUT_DLT, eventId, 1, Duration.ofSeconds(20))).hasSize(1);
            assertThat(receiversOf(eventId)).isEmpty();
        }
    }
}
