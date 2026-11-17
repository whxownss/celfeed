package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_event
            SET status = 'PUBLISHED'
            , published_at = :now
            , updated_at = :now
            WHERE id = :id
            AND status = 'PENDING' 
            """, nativeQuery = true)
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_event
            SET status = 'PUBLISHED'
            , published_at = :now
            , updated_at = :now
            WHERE id IN (:ids)
            AND status = 'PENDING'
            """, nativeQuery = true)
    int markPublishedIn(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);


    // 멀티 인스턴스 relay 대비 SKIP LOCKED
    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE status = 'PENDING'
            AND retry_at <= :now
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingForRelay(@Param("now") LocalDateTime now, @Param("limit") int limit);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_event
            SET status = CASE WHEN retry_count + 1 >= :maxRetry THEN 'FAILED' ELSE 'PENDING' END
            , retry_count = retry_count + 1
            , retry_at = :retryAt
            , last_error = :error
            , updated_at = :now
            WHERE id = :id
            AND status = 'PENDING'
            """, nativeQuery = true)
    int markRetry(@Param("id") Long id,
                  @Param("retryAt") LocalDateTime retryAt,
                  @Param("maxRetry") int maxRetry,
                  @Param("error") String error,
                  @Param("now") LocalDateTime now);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE outbox_event
            SET status = 'FAILED'
            , last_error = :error
            , updated_at = :now
            WHERE id = :id
            AND status = 'PENDING'
            """, nativeQuery = true)
    int markFailed(@Param("id") Long id, @Param("error") String error, @Param("now") LocalDateTime now);
}
