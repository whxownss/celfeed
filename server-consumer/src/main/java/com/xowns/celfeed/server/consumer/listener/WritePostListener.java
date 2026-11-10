package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.domain.social.dto.FollowerDTO;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.domain.social.repository.FollowRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import com.xowns.celfeed.server.consumer.config.KafkaConsumerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WritePostListener {

    @Value("${app.kafka.consumers.write-post.batch-size}")
    private int batchSize;

    private final ObjectMapper objectMapper;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final FollowQueryRepository followQueryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = KafkaTopic.WRITE_POST, containerFactory = KafkaConsumerConfig.WRITE_POST_CONTAINER_FACTORY)
    public void listener(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("[WRITE-POST] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        String payload = record.value();
        if (payload == null) {
            log.warn("[WRITE-POST] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();

            return;
        }

        WritePostEvent event = objectMapper.readValue(payload, WritePostEvent.class);

        log.info("[WRITE-POST] 페이로드 : eventId={}, postId={}, actorId={}, occurredAt={}",
                event.getEventId(),
                event.getPostId(),
                event.getActorId(),
                event.getOccurredAt());

        process(event, ack);
    }

    private void process(WritePostEvent event, Acknowledgment ack) {
        String eventId = event.getEventId();
        Long postId = event.getPostId();

        Post post = postRepository.findGraphById(postId).orElse(null);

        if (post == null) {
            log.error("[WRITE-POST] 존재하지 않는 게시글 : eventId={}, postId={}", eventId, postId);
            ack.acknowledge();
            return;
        }

        Member postWriter = post.getMember();
        Long cursorId = null;

        Long cursorFollowerId = null;

        while (true) {
            Long boundaryFollowerId =
                    followQueryRepository.findBoundaryFollowerId(event.getActorId(), cursorFollowerId, batchSize);

            // 경계값(cursor, boundary)을 kafka로 전달해서 두 번째 Consumer에서 조회 후 처리

            if (boundaryFollowerId == null) break;
            cursorFollowerId = boundaryFollowerId;
        }
    }

}
