package com.xowns.cefeed.server.consumer.listener;

import com.xowns.cefeed.server.consumer.service.NotificationCommandService;
import com.xowns.celfeed.domain.notification.dto.NotificationBulkDTO;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationCommandService notificationCommandService;
    private final LikeRepository likeRepository;

    @KafkaListener(
            topics = "${app.kafka.topics.noti-batch}", concurrency = "3",
            containerFactory = "writePostNotiContainerFactory"
    )
    public void writePostNotificationListener(WritePostNotiMessage message, ConsumerRecord<String, WritePostNotiMessage> record) {
        Map<Long, List<Long>> evenOddMap = message.getFollowerIds().stream()
                .collect(Collectors.groupingBy(followerId -> followerId % 2));

        evenOddMap.forEach((shardKey, ids) -> saveWritePostNotification(shardKey, ids, message));
    }

    private void saveWritePostNotification(Long shardKey, List<Long> ids, WritePostNotiMessage message) {
        List<NotificationBulkDTO> bulkList = ids.stream()
                .map(followerId ->
                        new NotificationBulkDTO(
                                followerId,
                                message.getWriterId(),
                                NotificationType.WRITE_POST.name(),
                                message.getPostId()
                        )
                ).toList();

        notificationCommandService.saveWritePostNotification(
                shardKey,
                bulkList,
                message.getActorNickname()
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.like-post}", groupId = "${app.kafka.consumers.noti-likepost.group-id}")
    public void likePostNotificationListener(Long likeId) {
        log.info("메시지 수신={}", likeId);

        Like like = likeRepository.findGraphById(likeId).orElse(null);
        if (like == null) return;

        Member receiver = like.getPost().getMember();
        Member actor = like.getMember();
        if (actor.equals(receiver)) return;

        notificationCommandService.saveLikePostNotification(
                receiver.getId(),
                actor.getId(),
                like.getPost().getId(),
                actor.getNickname()
        );
    }
}