package com.xowns.celfeed.server.notification.controller;

import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.server.notification.dto.NotificationResponse;
import com.xowns.celfeed.server.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final long LOGIN_ID = 1L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationService notificationService;

    private static MockHttpSession loginSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, LOGIN_ID);
        return session;
    }

    private static NotificationResponse response() {
        Notification notification = Notification.create("event-1", LOGIN_ID, 2L, NotificationType.LIKE_POST, 100L);
        ReflectionTestUtils.setField(notification, "id", 10L);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 11, 25, 9, 5, 7, 123_000_000));
        return NotificationResponse.of(notification, "철수");
    }

    @Test
    @DisplayName("로그인하지 않으면 알림 API 를 호출할 수 없다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/notifications/10")).andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("알림 목록은 로그인 회원 id 와 페이지 정보로 조회하고, 응답 형식으로 내려준다")
    void getNotifications() throws Exception {
        given(notificationService.findAll(eq(LOGIN_ID), any()))
                .willReturn(SliceDTO.of(new SliceImpl<>(List.of(response()), PageRequest.of(1, 5), true)));

        mockMvc.perform(get("/api/notifications").param("page", "1").param("size", "5").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("알림 목록이 조회되었습니다."))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.content[0].message").value("[철수]님이 내 게시글에 좋아요를 눌렀습니다."))
                .andExpect(jsonPath("$.data.content[0].target").value("/api/posts/100"))
                .andExpect(jsonPath("$.data.content[0].receiverId").value(LOGIN_ID))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-11-25 09:05:07"))
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).findAll(eq(LOGIN_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    /** 현재 동작 기록 : Lombok 이 boolean isRead 의 getter 를 isRead() 로 만들어서 JSON 필드명이 "read" 가 된다 */
    @Test
    @DisplayName("[현재 동작] 읽음 여부는 JSON 에서 isRead 가 아니라 read 필드로 내려간다")
    void readFieldName() throws Exception {
        given(notificationService.findAll(eq(LOGIN_ID), any()))
                .willReturn(SliceDTO.of(new SliceImpl<>(List.of(response()))));

        mockMvc.perform(get("/api/notifications").session(loginSession()))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.content[0].isRead").doesNotExist());
    }

    @Test
    @DisplayName("알림 읽음 처리는 로그인 회원 id 와 알림 id 로 서비스를 호출한다")
    void readNotification() throws Exception {
        mockMvc.perform(patch("/api/notifications/10").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림이 읽음 처리되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(notificationService).readNotification(LOGIN_ID, 10L);
    }

    @Test
    @DisplayName("알림 삭제는 로그인 회원 id 와 알림 id 로 서비스를 호출한다")
    void deleteNotification() throws Exception {
        mockMvc.perform(delete("/api/notifications/10").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림이 삭제되었습니다."));

        verify(notificationService).deleteNotification(LOGIN_ID, 10L);
    }

    @Test
    @DisplayName("알림 id 가 숫자가 아니면 400 으로 응답한다")
    void invalidNotificationId() throws Exception {
        mockMvc.perform(patch("/api/notifications/abc").session(loginSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorData.notificationId").value("abc"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("서비스에서 MEMBER_NOT_FOUND 가 발생하면 404 로 응답한다")
    void memberNotFound() throws Exception {
        willThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND))
                .given(notificationService).readNotification(LOGIN_ID, 10L);

        mockMvc.perform(patch("/api/notifications/10").session(loginSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorMessage").value("존재하지 않는 회원입니다."));
    }
}
