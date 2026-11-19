package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.config.KafkaConsumerConfig;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class WritePostListener {

    private static final String EVENT_TYPE = "write-post";

    @Value("${app.kafka.consumers.write-post.batch-size}")
    private int batchSize;

    private final ObjectMapper objectMapper;
    private final PostRepository postRepository;
    private final FollowQueryRepository followQueryRepository;
    private final KafkaTemplate<String, WritePostFanoutEvent> kafkaTemplate;
    private final IdempotencyCache idempotencyCache;

    @KafkaListener(topics = KafkaTopic.WRITE_POST, containerFactory = KafkaConsumerConfig.WRITE_POST_CF)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[WRITE-POST] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.error("[WRITE-POST] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        WritePostEvent event = parse(payload);
        String eventId = event.getEventId();

        log.info("[WRITE-POST] 페이로드 : eventId={}, postId={}, actorId={}, occurredAt={}",
                event.getEventId(),
                event.getPostId(),
                event.getActorId(),
                event.getOccurredAt());

        // 1) 이미 경계값 전송을 끝내 이벤트면 skip (Producer 중복 발행 대비, 낭비 방지)
        if (idempotencyCache.isProcessed(EVENT_TYPE, eventId)) {
            log.info("[WRITE-POST] 이미 처리된 이벤트 skip : eventId={}", eventId);
            ack.acknowledge();
            return;
        }

        // 2) 경계값 메시지 전송 (Kafka 트랜잭션 안)
        process(event);

        // 3) 완료 마크 (트랜잭션 커밋 후)
        markProcessedAfterCommit(eventId);

        ack.acknowledge();
    }

    private WritePostEvent parse(String payload) {
        WritePostEvent event;

        try {
            event = objectMapper.readValue(payload, WritePostEvent.class);
        } catch (JacksonException e) {
            throw new NonRetryableConsumerException("[WRITE-POST] 역직렬화 실패 : payload=" + payload, e);
        }

        if (event.getEventId() == null || event.getPostId() == null || event.getActorId() == null) {
            throw new NonRetryableConsumerException("[WRITE-POST] 필수 값 누락 : payload=" + payload);
        }

        return event;
    }

    private void process(WritePostEvent event) {
        String eventId = event.getEventId();
        Long postId = event.getPostId();

        Post post = postRepository.findGraphById(postId).orElse(null);

        if (post == null) {
            log.warn("[WRITE-POST] 존재하지 않는 게시글 : eventId={}, postId={}", eventId, postId);
            return;
        }

        Long cursorFollowerId = null;

        while (true) {
            Long boundaryFollowerId =
                    followQueryRepository.findBoundaryFollowerId(event.getActorId(), cursorFollowerId, batchSize);

            log.info("[WRITE-POST] Producer 메시지 전송 : eventId={}, postId={}, authorId={}, startId={}, endId={}",
                    eventId, postId, event.getActorId(), cursorFollowerId, boundaryFollowerId);

            kafkaTemplate.send(
                    KafkaTopic.WRITE_POST_FANOUT,
                    eventId,
                    new WritePostFanoutEvent(
                            eventId,
                            postId,
                            event.getActorId(),
                            post.getMember().getNickname(),
                            cursorFollowerId,
                            boundaryFollowerId
                    )
            );

            if (boundaryFollowerId == null) break;
            cursorFollowerId = boundaryFollowerId;
        }
    }

    /**
     * Kafka 트랜잭션이 커밋된 뒤에만 완료 마크
     * 커밋은 됐는데 마크가 실패해도, fanout 단계의 DB 유니크 키가 막아줌
     * (커밋 전에 찍으면, 마킹 후 롤백 되는 경우 재시도 시 Processed 상태로 남아 계속 skip 됨)
     * */
    private void markProcessedAfterCommit(String eventId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencyCache.markProcessed(EVENT_TYPE, eventId);
            }
        });
    }
}
