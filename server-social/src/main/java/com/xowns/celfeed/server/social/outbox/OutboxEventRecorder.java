package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.*;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(SocialEvent socialEvent) {
        OutboxEvent outbox = switch (socialEvent) {
            case WritePostEvent writePostEvent -> writePostToOutbox(writePostEvent);
            case LikePostEvent likePostEvent -> likePostToOutbox(likePostEvent);
            case FollowMemberEvent followMemberEvent -> followMemberToOutbox(followMemberEvent);
        };

        log.info("[OutboxEvent] 생성 : eventId={}, eventType={}, aggregateId={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());

        OutboxEvent savedOutbox = outboxRepository.save(outbox);

        log.info("[OutboxEvent] 저장 : id={}, eventId={}, eventType={}, aggregateId={}",
                outbox.getId(), outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());

        publisher.publishEvent(new OutboxCreatedEvent(savedOutbox.getId()));

        log.info("[OutboxEvent] 발행 : eventId={}, eventType={}, aggregateId={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getAggregateId());
    }

    private OutboxEvent writePostToOutbox(WritePostEvent event) {
        return OutboxEvent.pending(
                event.getEventId(),
                EventType.WRITE_POST.name(),
                event.getPostId(),
                event.getPostId().toString(),
                objectMapper.writeValueAsString(event)
        );
    }

    private OutboxEvent likePostToOutbox(LikePostEvent event) {
        return OutboxEvent.pending(
                event.getEventId(),
                EventType.LIKE_POST.name(),
                event.getLikeId(),
                event.getPostId() + ":" + event.getActorId(), // postId로 하면 핫파티션 발생
                objectMapper.writeValueAsString(event)
        );
    }

    private OutboxEvent followMemberToOutbox(FollowMemberEvent event) {
        return OutboxEvent.pending(
                event.getEventId(),
                EventType.FOLLOW_MEMBER.name(),
                event.getFollowId(),
                event.getToId() + ":" + event.getFromId(),
                objectMapper.writeValueAsString(event)
        );
    }
}
