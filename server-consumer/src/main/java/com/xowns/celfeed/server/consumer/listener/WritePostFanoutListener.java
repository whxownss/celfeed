package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.server.consumer.config.KafkaConsumerConfig;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WritePostFanoutListener {

    @Value("${app.sharding.notification.mod}")
    private int mod;

    private final FollowQueryRepository followQueryRepository;
    private final NotificationService notificationService;
    private final NotificationSender sender;

    @KafkaListener(topics = KafkaTopic.WRITE_POST_FANOUT, containerFactory = KafkaConsumerConfig.WRITE_POST_FANOUT_CF)
    public void listener(ConsumerRecord<String, WritePostFanoutEvent> record, Acknowledgment ack) {
        log.info("[WRITE-POST-FANOUT] 메시지 수신 : partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());

        WritePostFanoutEvent event = record.value();
        if (event == null) {
            log.warn("[WRITE-POST-FANOUT] 빈 메시지 skip : partition={}, offset={}, key={}",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        log.info("[WRITE-POST-FANOUT] 페이로드 : eventId={}, postId={}, authorId={}, startId={}, endId={}",
                event.eventId(),
                event.postId(),
                event.authorId(),
                event.startFollowerId(),
                event.endFollowerId());

        process(event, ack);
    }

    private void process(WritePostFanoutEvent event, Acknowledgment ack) {
        List<Long> followerIds =
                followQueryRepository.findFollowerIds(event.authorId(), event.startFollowerId(), event.endFollowerId());

        log.info("[WRITE-POST-FANOUT] 팔로워 조회 : {}명", followerIds.size());

        if (followerIds.isEmpty()) {
            ack.acknowledge();
            return;
        }

        followerIds.stream()
                .collect(Collectors.groupingBy(followerId -> followerId % mod))
                .forEach((shardKey, ids) -> {
                    // 알림 저장
                    List<Long> savedNotificationIds =
                            notificationService.saveWritePostNotification(
                                    shardKey,
                                    ids,
                                    event.authorId(),
                                    event.postId()
                            );
                    log.info("[WRITE-POST-FANOUT] 알림 저장 : {}건", savedNotificationIds.size());

                    // 알림 전송
                    List<NotificationDto> sendData = notificationService.findNotifications(savedNotificationIds)
                            .stream()
                            .map(notification -> NotificationDto.of(notification, event.authorNickname()))
                            .toList();
                    sender.send(sendData);
                    log.info("[WRITE-POST-FANOUT] 알림 전송 : {}건", savedNotificationIds.size());
                });

        ack.acknowledge();
    }
}
