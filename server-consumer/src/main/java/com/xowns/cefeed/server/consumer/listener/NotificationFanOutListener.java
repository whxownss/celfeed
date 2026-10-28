package com.xowns.cefeed.server.consumer.listener;

import com.xowns.celfeed.domain.social.dto.FollowerDTO;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOutListener {

    private final int BATCH_SIZE = 100_000;
    private final PostRepository postRepository;
    private final FollowQueryRepository followQueryRepository;
    private final KafkaTemplate<String, WritePostNotiMessage> writePostNotiKafkaTemplate;

    @Value("${app.kafka.topics.noti-batch}")
    private String notiBatchTopic;

    @KafkaListener(topics = "${app.kafka.topics.write-post}", groupId = "${app.kafka.consumers.noti-fanout.group-id}")
    public void fanOutListener(Long postId) {
        log.info("메시지 수신={}", postId);

        Post post = postRepository.findGraphById(postId).orElse(null);
        if (post == null) return;

        Member postWriter = post.getMember();
        Long cursorId = null;

        while (true) {
            List<FollowerDTO> followers =
                    followQueryRepository.findFollowerIdsByCursor(postWriter, cursorId, BATCH_SIZE);
            if (followers.isEmpty()) break;

            List<Long> followerIds = followers.stream()
                    .map(FollowerDTO::getFollowerId)
                    .collect(Collectors.toList());

            writePostNotiKafkaTemplate.send(
                    notiBatchTopic,
                    new WritePostNotiMessage(followerIds, postWriter.getId(), postId, postWriter.getNickname())
            );

            cursorId = followers.getLast().getId();
        }
    }
}
