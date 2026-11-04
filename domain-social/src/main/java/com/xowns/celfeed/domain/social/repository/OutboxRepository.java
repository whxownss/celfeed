package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
}
