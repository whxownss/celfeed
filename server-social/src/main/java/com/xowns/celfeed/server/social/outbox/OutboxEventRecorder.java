package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

    public void recordWritePostEvent(Post post) {
        OutboxEvent outbox = OutboxEvent.create(
                "Post",
                post.getId(),
                writePostTopic,
                objectMapper.writeValueAsString(post)
        );

        outboxRepository.save(outbox);
    }

    public void recordLikePostEvent(Like like) {
        OutboxEvent outbox = OutboxEvent.create(
                "Like",
                like.getId(),
                likePostTopic,
                objectMapper.writeValueAsString(like)
        );

        outboxRepository.save(outbox);
    }
}
