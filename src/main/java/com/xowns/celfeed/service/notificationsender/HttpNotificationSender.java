package com.xowns.celfeed.service.notificationsender;

import com.xowns.celfeed.dto.notification.NotificationLikePost;
import com.xowns.celfeed.dto.notification.NotificationWritePost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Profile("dev")
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpNotificationSender implements NotificationSender {

    private final RestClient restClient;

    @Override
    public void sendWritePost(Long postId) {
        try {
            restClient.post()
                    .uri("/write-post")
                    .body(new NotificationWritePost(postId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("==== 알림 생성 완료 ====");
        } catch (Exception e) {
            log.error("==== 알림 생성 서버 오류 ====", e);
        }

    }

    @Override
    public void sendLikePost(Long postId, Long actorId) {
        try {
            restClient.post()
                    .uri("/like-post")
                    .body(new NotificationLikePost(postId, actorId))
                    .retrieve()
                    .body(Void.class);
            log.info("==== 알림 생성 완료 ====");
        } catch (Exception e) {
            log.error("==== 알림 생성 서버 오류 ====", e);
        }
    }
}
