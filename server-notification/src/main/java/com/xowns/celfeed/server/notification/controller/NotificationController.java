package com.xowns.celfeed.server.notification.controller;

import com.xowns.celfeed.common.web.argumentresolver.Login;
import com.xowns.celfeed.common.web.dto.ApiResponse;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.server.notification.dto.NotificationResponse;
import com.xowns.celfeed.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @ResponseStatus(OK)
    public ApiResponse<SliceDTO<NotificationResponse>> getNotifications(@Login Long loginId, Pageable pageable) {
        SliceDTO<NotificationResponse> findNotifications = notificationService.findAll(loginId, pageable);
        return ApiResponse.of("알림 목록이 조회되었습니다.", findNotifications);
    }

    @PatchMapping("/{notificationId}")
    @ResponseStatus(OK)
    public ApiResponse<Void> readNotification(@Login Long loginId, @PathVariable Long notificationId) {
        notificationService.readNotification(loginId, notificationId);
        return ApiResponse.of("알림이 읽음 처리되었습니다.");
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(OK)
    public ApiResponse<Void> deleteNotification(@Login Long loginId, @PathVariable Long notificationId) {
        notificationService.deleteNotification(loginId, notificationId);
        return ApiResponse.of("알림이 삭제되었습니다.");
    }
}
