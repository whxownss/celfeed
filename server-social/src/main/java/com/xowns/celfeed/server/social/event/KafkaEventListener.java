package com.xowns.celfeed.server.social.event;

import com.xowns.celfeed.server.social.event.payload.LikePostEvent;
import com.xowns.celfeed.server.social.event.payload.WritePostEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class KafkaEventListener {

    private final KafkaTemplate<String, Long> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void writePost(WritePostEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getPostId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void likePost(LikePostEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getLikeId());
    }
}
