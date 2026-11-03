package com.xowns.celfeed.server.social.event;

import com.xowns.celfeed.server.social.event.payload.LikePostEvent;
import com.xowns.celfeed.server.social.event.payload.WritePostEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class KafkaEventListener {

    private final KafkaTemplate<String, Long> kafkaTemplate;

    @Value("${app.kafka.topics.write-post}")
    private String writePostTopic;

    @Value("${app.kafka.topics.like-post}")
    private String likePostTopic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void writePost(WritePostEvent event) {
        kafkaTemplate.send(writePostTopic, event.getPostId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void likePost(LikePostEvent event) {
        kafkaTemplate.send(likePostTopic, event.getLikeId());
    }
}
