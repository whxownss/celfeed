package com.xowns.celfeed.service.notification.kafka;

import com.xowns.celfeed.common.consts.KafkaGroupConst;
import com.xowns.celfeed.common.consts.KafkaTopicConst;
import com.xowns.celfeed.domain.basic.Like;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.notification.Notification;
import com.xowns.celfeed.domain.notification.NotificationType;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.basic.LikeRepository;
import com.xowns.celfeed.repository.notification.NotificationBulkRepository;
import com.xowns.celfeed.repository.notification.NotificationRepository;
import com.xowns.celfeed.service.basic.EmitterService;
import com.xowns.celfeed.service.notification.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Profile("dev")
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationCommandService notificationCommandService;
    private final LikeRepository likeRepository;

    @KafkaListener(
            topics = KafkaTopicConst.NOTI_BATCH, concurrency = "3",
            containerFactory = "writePostNotiContainerFactory"
    )
    public void writePostNotificationListener(WritePostNotiMessage message, ConsumerRecord<String, WritePostNotiMessage> record) {


        log.info("리스너 메서드 들어오고 sleep 직전");
        log.info("offset={}, partition={}",
                record.offset(),
                record.partition());

        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("시발여기: {}, Message: {}", Thread.currentThread().getName(), message.getFollowerIds().get(0));

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

    @KafkaListener(topics = KafkaTopicConst.LIKE_POST, groupId = KafkaGroupConst.NOTI_LIKEPOST)
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