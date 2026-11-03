package com.xowns.cefeed.server.consumer.service;

import com.xowns.celfeed.domain.notification.common.annotation.NotificationTransactional;
import com.xowns.celfeed.domain.notification.dto.NotificationBulkDTO;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.notification.repository.NotificationBulkRepository;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import com.xowns.celfeed.infra.sharding.core.Sharding;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Sharding(target = ShardingTarget.NOTIFICATION)
@NotificationTransactional
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    //private final EmitterService emitterService;

    public void saveWritePostNotification(Long shardKey, List<NotificationBulkDTO> bulkList, String actorNickname) {
        List<Long> generatedKeys = notificationBulkRepository.batchInsert(bulkList);

//        List<NotificationResponse> sendData = notificationRepository.findByIdIn(generatedKeys)
//                .stream()
//                .map(notification -> NotificationResponse.of(notification, actorNickname))
//                .toList();

        //emitterService.sendNotifications(sendData);
    }

    public void saveLikePostNotification(Long receiverId, Long actorId, Long postId, String actorNickname) {
        Notification savedNotification = notificationRepository.save(
                Notification.create(receiverId, actorId, NotificationType.LIKE_POST, postId)
        );

        //emitterService.sendNotification(NotificationResponse.of(savedNotification, actorNickname));
    }
}
