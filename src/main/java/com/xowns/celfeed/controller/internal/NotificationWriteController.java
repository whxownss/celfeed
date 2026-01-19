package com.xowns.celfeed.controller.internal;

import com.xowns.celfeed.dto.notification.NotificationLikePost;
import com.xowns.celfeed.dto.notification.NotificationWritePost;
import com.xowns.celfeed.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("dev-notification")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/notifications")
public class NotificationWriteController {

    private final NotificationService notificationService;

    @PostMapping("/write-post")
    public void writePost(@RequestBody NotificationWritePost writePost) {
        notificationService.requestWritePostNotification(writePost.getPostId());
    }

    @PostMapping("/like-post")
    public void likePost(@RequestBody NotificationLikePost likePost) {
        notificationService.requestLikePostNotification(likePost.getPostId(), likePost.getActorId());
    }
}
