package com.xowns.celfeed.server.consumer.sender;

import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class NotificationSenderResolverTest {

    @Test
    @DisplayName("지원하는 발송 수단 중 첫 번째를 선택한다")
    void firstSupported() {
        NotificationSender unsupported = mock(NotificationSender.class);
        NotificationSender supported = mock(NotificationSender.class);
        given(unsupported.isSupported()).willReturn(false);
        given(supported.isSupported()).willReturn(true);

        NotificationSenderResolver resolver = new NotificationSenderResolver(List.of(unsupported, supported));

        assertThat(resolver.resolve()).isSameAs(supported);
    }

    @Test
    @DisplayName("지원하는 발송 수단이 없으면 예외를 던진다")
    void noneSupported() {
        NotificationSender unsupported = mock(NotificationSender.class);
        given(unsupported.isSupported()).willReturn(false);

        assertThatThrownBy(() -> new NotificationSenderResolver(List.of(unsupported)).resolve())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("기본 발송 수단(로그)은 항상 지원된다")
    void logSenderSupported() {
        assertThat(new LogNotificationSender().isSupported()).isTrue();
    }

    @Test
    @DisplayName("발송 데이터는 알림의 메시지/이동 경로/읽음 여부/수신자/생성 시각을 담는다")
    void notificationDto() {
        Notification notification = Notification.create("e", 10L, 2L, NotificationType.LIKE_POST, 30L);
        ReflectionTestUtils.setField(notification, "id", 1L);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 11, 25, 10, 0));

        NotificationDto dto = NotificationDto.of(notification, "철수");

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getMessage()).isEqualTo("[철수]님이 내 게시글에 좋아요를 눌렀습니다.");
        assertThat(dto.getTarget()).isEqualTo("/api/posts/30");
        assertThat(dto.isRead()).isFalse();
        assertThat(dto.getReceiverId()).isEqualTo(10L);
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
