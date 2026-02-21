package com.xowns.celfeed.service.notification;

import com.xowns.celfeed.config.sharding.Sharding;
import com.xowns.celfeed.config.sharding.ShardingTarget;
import com.xowns.celfeed.domain.notification.Notification;
import com.xowns.celfeed.domain.notification.NotificationType;
import com.xowns.celfeed.dto.notification.NotificationBulkDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.repository.notification.NotificationBulkRepository;
import com.xowns.celfeed.repository.notification.NotificationRepository;
import com.xowns.celfeed.service.basic.EmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Sharding(target = ShardingTarget.NOTIFICATION)
@Transactional(value = "notificationTransactionManager")
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final EmitterService emitterService;

    public void saveWritePostNotification(Long shardKey, List<NotificationBulkDTO> bulkList, String actorNickname) {
        System.out.println("siuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu: " + shardKey);

        // jdbc 쓰는거 주의점!!!!!!
        List<Long> generatedKeys = notificationBulkRepository.batchInsert(bulkList);

        List<NotificationResponse> sendData = notificationRepository.findByIdIn(generatedKeys)
                .stream()
                .map(notification -> NotificationResponse.of(notification, actorNickname))
                .toList();

        emitterService.sendNotifications(sendData);
    }

    public void saveLikePostNotification(Long receiverId, Long actorId, Long postId, String actorNickname) {
        Notification savedNotification = notificationRepository.save(
                Notification.create(receiverId, actorId, NotificationType.LIKE_POST, postId)
        );

        emitterService.sendNotification(NotificationResponse.of(savedNotification, actorNickname));
    }
}
