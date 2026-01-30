package com.xowns.celfeed.service.kafka;

import com.xowns.celfeed.common.consts.KafkaGroupConst;
import com.xowns.celfeed.common.consts.KafkaTopicConst;
import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Post;
import com.xowns.celfeed.repository.FollowRepository;
import com.xowns.celfeed.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOutListener {

    private final int BATCH_SIZE = 100_000;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final KafkaTemplate<String, WritePostNotiMessage> writePostNotiKafkaTemplate;

    @KafkaListener(topics = KafkaTopicConst.WRITE_POST, groupId = KafkaGroupConst.NOTI_FANOUT)
    public void fanOutListener(Long postId) {
        log.info("메시지 수신={}", postId);

        Post post = postRepository.findById(postId).orElse(null);
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

            // send()
            writePostNotiKafkaTemplate.send(
                    KafkaTopicConst.NOTI_BATCH,
                    new WritePostNotiMessage(followerIds, postWriter.getId(), postId)
            );

            pageNumber++;
        }
    }
}
