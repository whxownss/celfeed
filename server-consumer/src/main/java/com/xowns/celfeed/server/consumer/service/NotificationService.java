package com.xowns.celfeed.server.consumer.service;

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
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;

    public Long saveLikePostNotification(Long receiverId, Long actorId, Long postId) {
        Notification notification = Notification.create(
                receiverId,
                actorId,
                NotificationType.LIKE_POST,
                postId
        );

        return notificationRepository.save(notification).getId();
    }

    public List<Long> saveWritePostNotification(Long shardKey, List<Long> receiverIds, Long actorId, Long postId) {
        List<NotificationBulkDTO> bulkList = receiverIds.stream()
                .map(receiverId ->
                        new NotificationBulkDTO(
                                receiverId,
                                actorId,
                                NotificationType.WRITE_POST.name(),
                                postId
                        )
                ).toList();

        return notificationBulkRepository.batchInsert(bulkList);
    }
}
