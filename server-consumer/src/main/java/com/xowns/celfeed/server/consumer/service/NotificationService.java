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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Sharding(target = ShardingTarget.NOTIFICATION)
@NotificationTransactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;

    public Notification saveLikePostNotification(Long receiverId, Long actorId, Long postId, String eventId) {
        Notification notification = Notification.create(
                eventId,
                receiverId,
                actorId,
                NotificationType.LIKE_POST,
                postId
        );

        return notificationRepository.save(notification);
    }

    public List<Notification> saveWritePostNotification(Long shardKey,
                                                        List<Long> receiverIds,
                                                        Long actorId,
                                                        Long postId,
                                                        String eventId) {

        List<NotificationBulkDTO> bulkList = receiverIds.stream()
                .map(receiverId ->
                        new NotificationBulkDTO(
                                eventId,
                                receiverId,
                                actorId,
                                NotificationType.WRITE_POST.name(),
                                postId
                        )
                ).toList();

        Set<Long> attemptedIds = new HashSet<>(notificationBulkRepository.batchInsertIgnoreDuplicates(bulkList));

        //  db에 저장된 id가 attemptedIds(이번에 저장 시도한 id 목록)에 포함된 경우만 반환 - 예전에 저장된 거는 반환 x
        return notificationRepository.findByEventIdAndReceiverIdIn(eventId, receiverIds).stream()
                .filter(notification -> attemptedIds.contains(notification.getId()))
                .toList();
    }

    @NotificationTransactional(readOnly = true)
    public List<Notification> findNotifications(List<Long> ids) {
        return notificationRepository.findByIdIn(ids);
    }
}
