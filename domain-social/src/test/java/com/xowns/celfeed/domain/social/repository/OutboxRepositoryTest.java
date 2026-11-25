package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRepositoryTest extends SocialRepositoryTestSupport {

    private static final int MAX_RETRY = 3;

    @Autowired
    OutboxRepository outboxRepository;

    private OutboxEvent pending() {
        return pending(UUID.randomUUID().toString());
    }

    private OutboxEvent pending(String eventId) {
        OutboxEvent outbox = OutboxEvent.pending(eventId, "LIKE_POST", 1L, "1:2", "{}");
        em.persist(outbox);
        return outbox;
    }

    /** 생성/재시도 시각처럼 엔티티가 now() 로 정하는 값을 테스트용으로 바꿀 때 사용 */
    private void updateColumn(Long id, String column, Object value) {
        em.createNativeQuery("UPDATE outbox_event SET " + column + " = :value WHERE id = :id")
                .setParameter("value", value)
                .setParameter("id", id)
                .executeUpdate();
    }

    private OutboxEvent reload(Long id) {
        em.clear();
        return outboxRepository.findById(id).orElseThrow();
    }

    // ========================================================================================

    @Test
    @DisplayName("PENDING 으로 저장되고, 재시도 횟수 0, 재시도 시각은 생성 시각과 같다")
    void savePending() {
        OutboxEvent outbox = pending();
        flushAndClear();

        OutboxEvent found = reload(outbox.getId());
        assertThat(found.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(found.getRetryCount()).isZero();
        assertThat(found.getRetryAt()).isEqualTo(found.getCreatedAt());
        assertThat(found.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("같은 eventId 는 두 번 저장할 수 없다")
    void duplicateEventId() {
        String eventId = UUID.randomUUID().toString();
        outboxRepository.saveAndFlush(OutboxEvent.pending(eventId, "LIKE_POST", 1L, "k", "{}"));

        assertThatThrownBy(() ->
                outboxRepository.saveAndFlush(OutboxEvent.pending(eventId, "LIKE_POST", 1L, "k", "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Nested
    @DisplayName("findPendingForRelay")
    class FindPendingForRelay {

        @Test
        @DisplayName("재시도 시각이 지난 PENDING 만 생성 순서대로 가져온다")
        void onlyDuePendingInCreatedOrder() {
            LocalDateTime now = LocalDateTime.now();
            OutboxEvent newer = pending();
            OutboxEvent older = pending();
            OutboxEvent notDue = pending();
            OutboxEvent published = pending();
            em.flush();
            updateColumn(newer.getId(), "created_at", now.minusMinutes(1));
            updateColumn(older.getId(), "created_at", now.minusMinutes(2));
            updateColumn(notDue.getId(), "retry_at", now.plusMinutes(1));     // 아직 재시도 시각 전
            updateColumn(published.getId(), "status", "PUBLISHED");

            // 조회 기준 시각은 저장 이후여야 함 (retry_at 은 저장 시점의 now 로 설정됨)
            List<OutboxEvent> result = outboxRepository.findPendingForRelay(now.plusSeconds(1), 10);

            assertThat(result).extracting(OutboxEvent::getId).containsExactly(older.getId(), newer.getId());
        }

        @Test
        @DisplayName("limit 개수만큼만 가져온다")
        void limit() {
            for (int i = 0; i < 5; i++) {
                pending();
            }
            em.flush();

            assertThat(outboxRepository.findPendingForRelay(LocalDateTime.now().plusSeconds(1), 3)).hasSize(3);
        }
    }

    @Nested
    @DisplayName("markPublished / markPublishedIn")
    class MarkPublished {

        @Test
        @DisplayName("PENDING 을 PUBLISHED 로 바꾸고 발행 시각을 기록한다")
        void markPublished() {
            OutboxEvent outbox = pending();
            em.flush();
            LocalDateTime now = LocalDateTime.now();

            int updated = outboxRepository.markPublished(outbox.getId(), now);

            assertThat(updated).isEqualTo(1);
            OutboxEvent found = reload(outbox.getId());
            assertThat(found.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(found.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 PUBLISHED 면 0 을 반환한다 (즉시 발행과 fallback relay 가 겹친 경우)")
        void alreadyPublished() {
            OutboxEvent outbox = pending();
            em.flush();
            outboxRepository.markPublished(outbox.getId(), LocalDateTime.now());

            assertThat(outboxRepository.markPublished(outbox.getId(), LocalDateTime.now())).isZero();
        }

        @Test
        @DisplayName("markPublishedIn 은 목록 중 PENDING 인 것만 PUBLISHED 로 바꾼다")
        void markPublishedIn() {
            OutboxEvent a = pending();
            OutboxEvent b = pending();
            OutboxEvent failed = pending();
            em.flush();
            updateColumn(failed.getId(), "status", "FAILED");

            int updated = outboxRepository.markPublishedIn(
                    List.of(a.getId(), b.getId(), failed.getId()), LocalDateTime.now());

            assertThat(updated).isEqualTo(2);
            assertThat(reload(a.getId()).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(reload(failed.getId()).getStatus()).isEqualTo(OutboxStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("markRetry / markFailed")
    class MarkRetryAndFailed {

        @Test
        @DisplayName("재시도 횟수를 1 올리고, 다음 재시도 시각과 에러를 기록한다 (상한 전이면 PENDING 유지)")
        void markRetry() {
            OutboxEvent outbox = pending();
            em.flush();
            LocalDateTime retryAt = LocalDateTime.now().plusSeconds(4).withNano(0);

            int updated = outboxRepository.markRetry(outbox.getId(), retryAt, MAX_RETRY, "TimeoutException: x", LocalDateTime.now());

            assertThat(updated).isEqualTo(1);
            OutboxEvent found = reload(outbox.getId());
            assertThat(found.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(found.getRetryCount()).isEqualTo(1);
            assertThat(found.getRetryAt()).isEqualTo(retryAt);
            assertThat(found.getLastError()).isEqualTo("TimeoutException: x");
        }

        @Test
        @DisplayName("재시도 횟수가 상한에 도달하면 FAILED 가 된다")
        void markRetryReachesMax() {
            OutboxEvent outbox = pending();
            em.flush();

            for (int i = 1; i <= MAX_RETRY; i++) {
                outboxRepository.markRetry(outbox.getId(), LocalDateTime.now(), MAX_RETRY, "error " + i, LocalDateTime.now());
                OutboxEvent found = reload(outbox.getId());

                assertThat(found.getRetryCount()).isEqualTo(i);
                assertThat(found.getStatus())
                        .isEqualTo(i < MAX_RETRY ? OutboxStatus.PENDING : OutboxStatus.FAILED);
            }

            // FAILED 이후에는 더 이상 갱신되지 않음
            assertThat(outboxRepository.markRetry(outbox.getId(), LocalDateTime.now(), MAX_RETRY, "e", LocalDateTime.now()))
                    .isZero();
        }

        @Test
        @DisplayName("markFailed 는 PENDING 을 즉시 FAILED 로 바꾸고, 이미 처리된 건 바꾸지 않는다")
        void markFailed() {
            OutboxEvent pending = pending();
            OutboxEvent published = pending();
            em.flush();
            updateColumn(published.getId(), "status", "PUBLISHED");

            assertThat(outboxRepository.markFailed(pending.getId(), "IllegalArgumentException: x", LocalDateTime.now()))
                    .isEqualTo(1);
            assertThat(outboxRepository.markFailed(published.getId(), "e", LocalDateTime.now())).isZero();

            OutboxEvent found = reload(pending.getId());
            assertThat(found.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(found.getLastError()).isEqualTo("IllegalArgumentException: x");
            assertThat(reload(published.getId()).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("deleteByStatusBefore")
    class DeleteByStatusBefore {

        @Test
        @DisplayName("기준 시각 이전에 생성된 해당 상태의 이벤트만, 오래된 순으로 limit 개까지 삭제한다")
        void deleteOldPublished() {
            LocalDateTime now = LocalDateTime.now();
            OutboxEvent oldest = pending();
            OutboxEvent old = pending();
            OutboxEvent oldButPending = pending();
            OutboxEvent recent = pending();
            em.flush();
            updateColumn(oldest.getId(), "created_at", now.minusHours(10));
            updateColumn(old.getId(), "created_at", now.minusHours(8));
            updateColumn(oldButPending.getId(), "created_at", now.minusHours(9));
            for (OutboxEvent e : List.of(oldest, old, recent)) {
                updateColumn(e.getId(), "status", "PUBLISHED");
            }

            int firstDeleted = outboxRepository.deleteByStatusBefore("PUBLISHED", now.minusHours(6), 1);
            em.clear();

            assertThat(firstDeleted).isEqualTo(1);
            assertThat(outboxRepository.findById(oldest.getId())).isEmpty(); // 가장 오래된 것부터

            int secondDeleted = outboxRepository.deleteByStatusBefore("PUBLISHED", now.minusHours(6), 10);
            em.clear();

            assertThat(secondDeleted).isEqualTo(1);
            assertThat(outboxRepository.findById(old.getId())).isEmpty();
            assertThat(outboxRepository.findById(oldButPending.getId())).isPresent(); // 상태가 다름
            assertThat(outboxRepository.findById(recent.getId())).isPresent();        // 기준 시각 이후
        }
    }
}
