package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.argumentresolver.Login;
import com.xowns.celfeed.controller.response.ApiResponse;
import com.xowns.celfeed.controller.response.ResponseEntityUtils;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.dto.notification.NotificationResponse;
import com.xowns.celfeed.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<SliceDTO<NotificationResponse>>> getNotifications(@Login Long loginId, Pageable pageable) {
        return ResponseEntityUtils.ok("알림 목록이 조회되었습니다.", notificationService.findAll(loginId, pageable));
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> readNotification(@Login Long loginId, @PathVariable Long notificationId) {
        notificationService.readNotification(loginId, notificationId);
        return ResponseEntityUtils.ok("알림 읽음 처리");
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@Login Long loginId, @PathVariable Long notificationId) {
        notificationService.deleteNotification(loginId, notificationId);
        return ResponseEntityUtils.ok("알림 삭제");
    }
}
