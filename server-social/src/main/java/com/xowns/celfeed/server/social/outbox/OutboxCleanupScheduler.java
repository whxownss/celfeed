package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    @Value("${app.outbox.scheduler.batch-size}")
    private int batchSize;

    @Value("${app.outbox.scheduler.ttl-hours}")
    private int ttlHours;

    private final OutboxRepository outboxRepository;

    @Scheduled(fixedDelayString = "${app.outbox.scheduler.fixed-delay}")
    @Transactional
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();

        int deleted = outboxRepository.deleteByStatusBefore(
                OutboxStatus.PUBLISHED.name(), now.minusHours(ttlHours), batchSize
        );

        log.info("[Outbox Cleanup] PUBLISHED 삭제 : {}건", deleted);
    }
}
