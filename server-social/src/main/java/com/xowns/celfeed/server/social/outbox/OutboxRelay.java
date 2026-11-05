package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.server.social.config.async.AsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class OutboxRelay {


    @Async(AsyncConfig.OUTBOX_RELAY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(OutboxCreatedEvent event) {


    }
}
