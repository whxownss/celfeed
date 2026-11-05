package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE outbox_event
        SET status = 'PUBLISHED' AND published_at = :now AND updated_at = :now
        WHERE id = :id AND status = 'PENDING' 
        """, nativeQuery = true)
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);
}
