package com.xowns.celfeed.server.consumer.service;

import com.xowns.celfeed.domain.notification.common.annotation.NotificationTransactional;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import com.xowns.celfeed.infra.sharding.core.Sharding;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Sharding(target = ShardingTarget.NOTIFICATION)
@NotificationTransactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Long saveLikePostNotification(Long receiverId, Long actorId, Long postId) {
        Notification notification = Notification.create(
                receiverId,
                actorId,
                NotificationType.LIKE_POST,
                postId
        );

        return notificationRepository.save(notification).getId();
    }
}
