package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.EventType;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.common.event.SocialEvent;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher publisher;

//    @Value("${app.kafka.topics.write-post}")
//    private String writePostTopic;
//
//    @Value("${app.kafka.topics.like-post}")
//    private String likePostTopic;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(SocialEvent socialEvent) {
        Long outboxId = switch (socialEvent) {
            case WritePostEvent writePostEvent -> recordWritePostEvent(writePostEvent);
            case LikePostEvent likePostEvent -> recordLikePostEvent(likePostEvent);
            default -> throw new ApiException(ErrorCode.UNSUPPORTED_EVENT_TYPE);
        };

        publisher.publishEvent(new OutboxCreatedEvent(outboxId));
    }

    public Long recordWritePostEvent(WritePostEvent writePostEvent) {
        OutboxEvent outbox = OutboxEvent.pending(
                writePostEvent.getEventId(),
                EventType.WRITE_POST.name(),
                writePostEvent.getPostId(),
                writePostEvent.getPostId().toString(),
                objectMapper.writeValueAsString(writePostEvent)
        );

        return outboxRepository.save(outbox).getId();
    }

    public Long recordLikePostEvent(LikePostEvent likePostEvent) {
        OutboxEvent outbox = OutboxEvent.pending(
                likePostEvent.getEventId(),
                EventType.LIKE_POST.name(),
                likePostEvent.getLikeId(),
                likePostEvent.getPostId().toString(),
                objectMapper.writeValueAsString(likePostEvent)
        );

        return outboxRepository.save(outbox).getId();
    }
}
