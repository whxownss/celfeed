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
            case LikePostEvent likePostEvent -> likePostToOutboxEvent(likePostEvent);
            default -> {
                String message = "지원하지 않는 이벤트 타입";
                log.error("{}", message);
                throw new IllegalArgumentException(message);
            }
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

    private OutboxEvent writePostToOutbox(WritePostEvent writePostEvent) {
        return OutboxEvent.pending(
                writePostEvent.getEventId(),
                EventType.WRITE_POST.name(),
                writePostEvent.getPostId(),
                writePostEvent.getPostId().toString(),
                objectMapper.writeValueAsString(writePostEvent)
        );
    }

    private OutboxEvent likePostToOutboxEvent(LikePostEvent likePostEvent) {
        return OutboxEvent.pending(
                likePostEvent.getEventId(),
                EventType.LIKE_POST.name(),
                likePostEvent.getLikeId(),
                likePostEvent.getPostId().toString(),
                objectMapper.writeValueAsString(likePostEvent)
        );
    }
}
