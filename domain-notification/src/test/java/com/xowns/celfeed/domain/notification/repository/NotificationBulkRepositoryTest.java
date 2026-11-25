package com.xowns.celfeed.domain.notification.repository;

import com.xowns.celfeed.domain.notification.dto.NotificationBulkDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationBulkRepositoryTest extends NotificationRepositoryTestSupport {

    @Autowired
    NotificationBulkRepository bulkRepository;

    @Autowired
    @Qualifier("notificationJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    private static NotificationBulkDTO dto(String eventId, long receiverId) {
        return new NotificationBulkDTO(eventId, receiverId, 100L, "WRITE_POST", 500L);
    }

    private long count(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE event_id = ?", Long.class, eventId);
    }

    // ========================================================================================
    // batchInsert
    // ========================================================================================

    @Test
    @DisplayName("batchInsert 는 모든 행을 저장하고, 생성한 id 를 입력 순서대로 반환한다")
    void batchInsert() {
        List<NotificationBulkDTO> bulk = List.of(dto("event-a", 1L), dto("event-a", 2L), dto("event-a", 3L));

        List<Long> ids = bulkRepository.batchInsert(bulk);

        assertThat(ids).hasSize(3).doesNotHaveDuplicates().isSorted();
        assertThat(ids).containsExactly(bulk.get(0).getId(), bulk.get(1).getId(), bulk.get(2).getId());
        assertThat(count("event-a")).isEqualTo(3);
    }

    @Test
    @DisplayName("저장된 행은 읽지 않음(N) 상태이고, 같은 배치는 같은 생성 시각을 가진다")
    void batchInsertColumns() {
        bulkRepository.batchInsert(List.of(dto("event-b", 1L), dto("event-b", 2L)));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT receiver_id, actor_id, type, target_id, is_read, created_at FROM notification WHERE event_id = ? ORDER BY receiver_id",
                "event-b");

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("actor_id")).isEqualTo(100L);
            assertThat(row.get("type")).isEqualTo("WRITE_POST");
            assertThat(row.get("target_id")).isEqualTo(500L);
            assertThat(row.get("is_read")).isEqualTo("N");
        });
        assertThat(rows.get(0).get("created_at")).isEqualTo(rows.get(1).get("created_at"));
    }

    @Test
    @DisplayName("batchInsert 는 같은 배치 안의 중복(eventId + receiverId)을 무시하지 않고 예외를 던진다")
    void batchInsertDuplicateThrows() {
        List<NotificationBulkDTO> bulk = List.of(dto("event-c", 1L), dto("event-c", 1L));

        assertThatThrownBy(() -> bulkRepository.batchInsert(bulk))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ========================================================================================
    // batchInsertIgnoreDuplicates
    // ========================================================================================

    @Test
    @DisplayName("batchInsertIgnoreDuplicates 는 중복이 없으면 batchInsert 와 똑같이 저장한다")
    void ignoreDuplicatesWithoutDuplicate() {
        List<Long> ids = bulkRepository.batchInsertIgnoreDuplicates(
                List.of(dto("event-d", 1L), dto("event-d", 2L)));

        assertThat(ids).hasSize(2).doesNotHaveDuplicates();
        assertThat(count("event-d")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 배치 안의 중복은 예외 없이 무시되어 1건만 저장되고, 저장된 건 먼저 들어온 id 다")
    void ignoreDuplicatesInSameBatch() {
        List<Long> attemptedIds = bulkRepository.batchInsertIgnoreDuplicates(
                List.of(dto("event-e", 1L), dto("event-e", 1L)));

        // 반환값은 "저장을 시도한" id 목록이라 무시된 것까지 2개
        assertThat(attemptedIds).hasSize(2);

        List<Long> storedIds = jdbcTemplate.queryForList(
                "SELECT id FROM notification WHERE event_id = ?", Long.class, "event-e");
        assertThat(storedIds).containsExactly(attemptedIds.get(0));
    }

    /**
     * 현재 동작 기록 (중요)
     * 유니크 키가 (event_id, receiver_id, created_at) 이고 created_at 은 호출할 때마다 now() 로 새로 정해진다.
     * 그래서 재시도처럼 "다른 호출"로 같은 eventId + receiverId 를 저장하면 유니크 키에 걸리지 않고 중복 저장된다.
     * → Redis 멱등성이 실패했을 때 DB 유니크 키가 중복을 막아주지 못한다.
     * 의도한 동작이 "1건만 저장" 이라면 유니크 키 설계를 바꾼 뒤 이 테스트의 기대값을 1로 바꾼다.
     */
    @Test
    @DisplayName("[현재 동작] 다른 호출(재시도)로 같은 eventId + receiverId 를 저장하면 created_at 이 달라 중복 저장된다")
    void duplicateAcrossCallsIsNotIgnored() throws InterruptedException {
        bulkRepository.batchInsertIgnoreDuplicates(List.of(dto("event-f", 1L)));
        Thread.sleep(5); // 재시도는 항상 이전 호출보다 늦게 일어난다
        bulkRepository.batchInsertIgnoreDuplicates(List.of(dto("event-f", 1L)));

        List<Timestamp> createdAts = jdbcTemplate.queryForList(
                "SELECT created_at FROM notification WHERE event_id = ?", Timestamp.class, "event-f");

        assertThat(createdAts).hasSize(2).doesNotHaveDuplicates();
    }
}
