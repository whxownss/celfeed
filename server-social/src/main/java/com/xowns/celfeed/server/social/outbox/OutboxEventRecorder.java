package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.common.event.SocialEvent;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    @Value("${app.kafka.topics.write-post}")
    private String writePostTopic;

    @Value("${app.kafka.topics.like-post}")
    private String likePostTopic;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(SocialEvent socialEvent) {
        switch (socialEvent) {
            case WritePostEvent writePostEvent -> recordWritePostEvent(writePostEvent);
            case LikePostEvent likePostEvent -> recordLikePostEvent(likePostEvent);
            default -> throw new ApiException(ErrorCode.UNSUPPORTED_EVENT_TYPE);
        }
    }

    public void recordWritePostEvent(WritePostEvent writePostEvent) {
        OutboxEvent outbox = OutboxEvent.pending(
                writePostEvent.getEventId(),
                writePostEvent.getPostId(),
                writePostTopic,
                objectMapper.writeValueAsString(writePostEvent)
        );

        outboxRepository.save(outbox);
    }

    public void recordLikePostEvent(LikePostEvent likePostEvent) {
        OutboxEvent outbox = OutboxEvent.pending(
                likePostEvent.getEventId(),
                likePostEvent.getLikeId(),
                likePostTopic,
                objectMapper.writeValueAsString(likePostEvent)
        );

        outboxRepository.save(outbox);
    }
}
