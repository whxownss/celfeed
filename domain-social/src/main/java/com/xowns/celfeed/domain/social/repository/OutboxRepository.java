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


    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingForRelay(@Param("limit") int limit);
}
