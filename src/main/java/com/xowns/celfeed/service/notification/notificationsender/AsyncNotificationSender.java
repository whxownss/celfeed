package com.xowns.celfeed.service.notification.notificationsender;

import com.xowns.celfeed.service.notification.NotificationOldCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//@Component
@RequiredArgsConstructor
public class AsyncNotificationSender implements NotificationSender {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void sendWritePost(Long postId) {
        eventPublisher.publishEvent(new WritePostEvent(postId));
    }

    @Override
    public void sendLikePost(Long likeId) {
        eventPublisher.publishEvent(new LikePostEvent(likeId));
    }

    @Component
    @RequiredArgsConstructor
    static class NotificationListener {

        private final NotificationOldCommandService notificationCommandService;

        @Async
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void handleWritePost(WritePostEvent event) {
            notificationCommandService.saveWritePostNotification(event.postId);
        }

        @Async
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void handleLikePost(LikePostEvent event) {
            notificationCommandService.saveLikePostNotification(event.likeId);
        }
    }

    @RequiredArgsConstructor
    static class WritePostEvent {
        private final Long postId;
    }

    @RequiredArgsConstructor
    static class LikePostEvent {
        private final Long likeId;
    }
}
