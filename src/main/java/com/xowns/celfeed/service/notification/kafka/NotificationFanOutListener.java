package com.xowns.celfeed.service.notification.kafka;

import com.xowns.celfeed.common.consts.KafkaGroupConst;
import com.xowns.celfeed.common.consts.KafkaTopicConst;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.Post;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.basic.FollowRepository;
import com.xowns.celfeed.repository.basic.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.internals.Acknowledgements;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Profile("dev")
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOutListener {

    private final ConfigurableApplicationContext context;

    private final int BATCH_SIZE = 100_000;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final KafkaTemplate<String, WritePostNotiMessage> writePostNotiKafkaTemplate;

    @KafkaListener(topics = KafkaTopicConst.WRITE_POST, groupId = KafkaGroupConst.NOTI_FANOUT)
    public void fanOutListener(Long postId) {
        log.info("메시지 수신={}", postId);

        Post post = postRepository.findGraphById(postId).orElse(null);
        if (post == null) return;

        Member postWriter = post.getMember();
        List<Long> followerIds;
        PageRequest pageRequest;
        int pageNumber = 0;

        // 팔로워 수에 따라?
        // TODO : offset paging -> cursor paging
        while (true) {
            pageRequest = PageRequest.of(pageNumber, BATCH_SIZE, Sort.by("id").ascending());
            followerIds = followRepository.findFollowerIdsByToMember(postWriter, pageRequest);
            if (followerIds.isEmpty()) break;

            writePostNotiKafkaTemplate.send(
                    KafkaTopicConst.NOTI_BATCH,
                    new WritePostNotiMessage(followerIds, postWriter.getId(), postId, postWriter.getNickname())
            );

            pageNumber++;
        }
    }
}
