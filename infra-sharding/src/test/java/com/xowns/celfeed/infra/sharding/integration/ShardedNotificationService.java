package com.xowns.celfeed.infra.sharding.integration;

import com.xowns.celfeed.domain.notification.common.annotation.NotificationTransactional;
import com.xowns.celfeed.domain.notification.dto.NotificationBulkDTO;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.notification.repository.NotificationBulkRepository;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import com.xowns.celfeed.infra.sharding.core.Sharding;
import com.xowns.celfeed.infra.sharding.core.ShardingTarget;

import java.util.List;
import java.util.Optional;

/** 실제 서비스(server-consumer NotificationService 등)와 같은 방식으로 @Sharding + @NotificationTransactional 을 쓰는 테스트용 서비스 */
@Sharding(target = ShardingTarget.NOTIFICATION)
@NotificationTransactional
public class ShardedNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationBulkRepository notificationBulkRepository;

    public ShardedNotificationService(NotificationRepository notificationRepository,
                                      NotificationBulkRepository notificationBulkRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationBulkRepository = notificationBulkRepository;
    }

    public Notification save(Long receiverId, String eventId) {
        return notificationRepository.save(
                Notification.create(eventId, receiverId, 100L, NotificationType.LIKE_POST, 500L));
    }

    @NotificationTransactional(readOnly = true)
    public Optional<Notification> find(Long shardKey, Long notificationId) {
        return notificationRepository.findById(notificationId);
    }

    public List<Long> bulkSave(Long shardKey, List<Long> receiverIds, String eventId) {
        List<NotificationBulkDTO> bulk = receiverIds.stream()
                .map(receiverId -> new NotificationBulkDTO(eventId, receiverId, 100L, "WRITE_POST", 500L))
                .toList();
        return notificationBulkRepository.batchInsertIgnoreDuplicates(bulk);
    }

    public void saveThenFail(Long receiverId, String eventId) {
        notificationRepository.saveAndFlush(
                Notification.create(eventId, receiverId, 100L, NotificationType.LIKE_POST, 500L));
        throw new IllegalStateException("rollback");
    }
}
