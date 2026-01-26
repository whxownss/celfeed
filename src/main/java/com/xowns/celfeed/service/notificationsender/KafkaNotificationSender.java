package com.xowns.celfeed.service.notificationsender;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class KafkaNotificationSender implements NotificationSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void sendWritePost(Long postId) {

    }

    @Override
    public void sendLikePost(Long likeId) {

    }
}
