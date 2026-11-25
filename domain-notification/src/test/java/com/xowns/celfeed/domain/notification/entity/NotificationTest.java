package com.xowns.celfeed.domain.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    @DisplayName("생성 시 전달한 값이 들어가고, 읽지 않은 상태로 시작한다")
    void create() {
        Notification notification = Notification.create("event-1", 10L, 20L, NotificationType.LIKE_POST, 30L);

        assertThat(notification.getEventId()).isEqualTo("event-1");
        assertThat(notification.getReceiverId()).isEqualTo(10L);
        assertThat(notification.getActorId()).isEqualTo(20L);
        assertThat(notification.getType()).isEqualTo(NotificationType.LIKE_POST);
        assertThat(notification.getTargetId()).isEqualTo(30L);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getId()).isNull();        // id 는 저장 시 Snowflake 로 생성
        assertThat(notification.getCreatedAt()).isNull(); // createdAt 은 저장 시 @PrePersist 로 설정
    }

    @Test
    @DisplayName("read() 를 호출하면 읽음 상태가 된다")
    void read() {
        Notification notification = Notification.create("event-1", 10L, 20L, NotificationType.LIKE_POST, 30L);

        notification.read();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("@PrePersist 는 생성 시각을 현재 시각으로 설정한다")
    void prePersistCreatedAt() {
        Notification notification = Notification.create("event-1", 10L, 20L, NotificationType.LIKE_POST, 30L);
        LocalDateTime before = LocalDateTime.now();

        notification.prePersistCreatedAt();

        assertThat(notification.getCreatedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("알림 타입별 메시지는 [닉네임] 뒤에 타입 메시지를 붙인다")
    void createMessage() {
        assertThat(Notification.create("e", 1L, 2L, NotificationType.LIKE_POST, 3L).createMessage("철수"))
                .isEqualTo("[철수]님이 내 게시글에 좋아요를 눌렀습니다.");
        assertThat(Notification.create("e", 1L, 2L, NotificationType.WRITE_POST, 3L).createMessage("철수"))
                .isEqualTo("[철수]님이 게시글을 작성했습니다.");
        assertThat(Notification.create("e", 1L, 2L, NotificationType.FOLLOW_MEMBER, 3L).createMessage("철수"))
                .isEqualTo("[철수]님이 회원님을 팔로우했습니다.");
    }

    @Test
    @DisplayName("이동 경로는 타입의 URI 뒤에 targetId 를 붙인다")
    void createTarget() {
        assertThat(Notification.create("e", 1L, 2L, NotificationType.LIKE_POST, 30L).createTarget())
                .isEqualTo("/api/posts/30");
        assertThat(Notification.create("e", 1L, 2L, NotificationType.WRITE_POST, 31L).createTarget())
                .isEqualTo("/api/posts/31");
    }

    /**
     * 현재 동작 기록 : FOLLOW_MEMBER 의 URI 는 "/" 로 끝나지 않아서 targetId(followId)가 바로 붙는다.
     * 의도한 경로가 "/api/follows/followers" 라면 수정이 필요하다.
     */
    @Test
    @DisplayName("[현재 동작] FOLLOW_MEMBER 이동 경로는 /api/follows/followers 뒤에 followId 가 구분자 없이 붙는다")
    void followMemberTargetCurrentBehavior() {
        assertThat(Notification.create("e", 1L, 2L, NotificationType.FOLLOW_MEMBER, 5L).createTarget())
                .isEqualTo("/api/follows/followers5");
    }
}
