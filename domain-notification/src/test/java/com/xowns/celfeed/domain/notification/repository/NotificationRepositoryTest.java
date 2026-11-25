package com.xowns.celfeed.domain.notification.repository;

import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest extends NotificationRepositoryTestSupport {

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    EntityManager em;

    @Autowired
    @Qualifier("notificationJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    private static final PageRequest LATEST_FIRST = PageRequest.of(0, 10, Sort.by("createdAt").descending());

    private long sequence = 1;

    /** 생성 시각을 직접 지정해야 하는 경우 (JPA 저장은 @PrePersist 가 항상 now() 로 덮어씀) */
    private long insert(String eventId, long receiverId, LocalDateTime createdAt) {
        long id = System.nanoTime() + sequence++;
        jdbcTemplate.update("""
                        INSERT INTO notification(id, event_id, receiver_id, actor_id, type, target_id, is_read, created_at)
                        VALUES (?, ?, ?, 100, 'LIKE_POST', 500, 'N', ?)
                        """,
                id, eventId, receiverId, Timestamp.valueOf(createdAt));
        return id;
    }

    private Notification save(String eventId, long receiverId) {
        Notification saved = notificationRepository.save(
                Notification.create(eventId, receiverId, 100L, NotificationType.LIKE_POST, 500L));
        em.flush();
        em.clear();
        return saved;
    }

    // ========================================================================================
    // 저장
    // ========================================================================================

    @Test
    @DisplayName("저장하면 Snowflake id 와 생성 시각이 채워지고, 다시 조회할 수 있다")
    void save() {
        Notification saved = save("event-1", 1L);

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();

        Notification found = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventId()).isEqualTo("event-1");
        assertThat(found.getType()).isEqualTo(NotificationType.LIKE_POST);
        assertThat(found.isRead()).isFalse();
    }

    @Test
    @DisplayName("읽음 처리하면 DB 에 Y 로 저장된다")
    void readIsStoredAsY() {
        Notification saved = save("event-2", 1L);

        Notification found = notificationRepository.findById(saved.getId()).orElseThrow();
        found.read();
        em.flush();

        String isRead = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notification WHERE id = ?", String.class, saved.getId());
        assertThat(isRead).isEqualTo("Y");
    }

    /**
     * 현재 동작 기록 (중요) : NotificationBulkRepositoryTest#duplicateAcrossCallsIsNotIgnored 와 같은 원인
     * consumer 의 like-post / follow-member 는 DataIntegrityViolationException(유니크 키 중복)을
     * "이미 처리된 이벤트" 로 판단하는데, created_at 이 매번 달라서 이 예외가 발생하지 않는다.
     */
    @Test
    @DisplayName("[현재 동작] JPA 로 같은 eventId + receiverId 를 두 번 저장해도 유니크 키 예외 없이 2건 저장된다")
    void duplicateSaveIsNotBlocked() throws InterruptedException {
        save("event-dup", 1L);
        Thread.sleep(5);
        save("event-dup", 1L);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE event_id = ?", Long.class, "event-dup");
        assertThat(count).isEqualTo(2);
    }

    // ========================================================================================
    // findByReceiver
    // ========================================================================================

    @Test
    @DisplayName("findByReceiver 는 해당 수신자의 기준 시각 이후 ~ 현재까지의 알림만 최신순으로 조회한다")
    void findByReceiver() {
        LocalDateTime now = LocalDateTime.now();
        long recent = insert("e-recent", 1L, now.minusDays(1));
        long older = insert("e-older", 1L, now.minusDays(10));
        insert("e-expired", 1L, now.minusDays(40));     // 기준 시각 이전
        insert("e-future", 1L, now.plusDays(1));        // 현재 시각 이후
        insert("e-other", 2L, now.minusDays(1));        // 다른 수신자

        Slice<Notification> result = notificationRepository.findByReceiver(1L, now.minusDays(29), LATEST_FIRST);

        assertThat(result.getContent()).extracting(Notification::getId).containsExactly(recent, older);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("findByReceiver 는 Slice 로 다음 페이지 존재 여부를 알려준다")
    void findByReceiverSlice() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 3; i++) {
            insert("e-slice-" + i, 1L, now.minusHours(i));
        }

        Slice<Notification> first = notificationRepository.findByReceiver(
                1L, now.minusDays(29), PageRequest.of(0, 2, Sort.by("createdAt").descending()));
        Slice<Notification> second = notificationRepository.findByReceiver(
                1L, now.minusDays(29), PageRequest.of(1, 2, Sort.by("createdAt").descending()));

        assertThat(first.getContent()).extracting(Notification::getEventId).containsExactly("e-slice-1", "e-slice-2");
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(Notification::getEventId).containsExactly("e-slice-3");
        assertThat(second.hasNext()).isFalse();
    }

    // ========================================================================================
    // findByIdIn / findByEventIdAndReceiverIdIn
    // ========================================================================================

    @Test
    @DisplayName("findByIdIn 은 전달한 id 의 알림만 조회한다")
    void findByIdIn() {
        LocalDateTime now = LocalDateTime.now();
        long a = insert("e-in", 1L, now);
        long b = insert("e-in", 2L, now);
        insert("e-in", 3L, now);

        List<Notification> result = notificationRepository.findByIdIn(List.of(a, b));

        assertThat(result).extracting(Notification::getId).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("findByEventIdAndReceiverIdIn 은 같은 이벤트의, 전달한 수신자 알림만 조회한다")
    void findByEventIdAndReceiverIdIn() {
        LocalDateTime now = LocalDateTime.now();
        long r1 = insert("event-x", 1L, now);
        long r2 = insert("event-x", 2L, now);
        insert("event-x", 3L, now);     // 대상 수신자 아님
        insert("event-y", 1L, now);     // 다른 이벤트

        List<Notification> result = notificationRepository.findByEventIdAndReceiverIdIn("event-x", List.of(1L, 2L));

        assertThat(result).extracting(Notification::getId).containsExactlyInAnyOrder(r1, r2);
    }
}
