package com.xowns.celfeed.server.notification.service;

import com.xowns.celfeed.common.web.dto.SliceDTO;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.notification.repository.NotificationRepository;
import com.xowns.celfeed.domain.social.dto.MemberIdNickname;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.server.notification.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * NotificationService 단위 테스트 (리포지토리는 Mockito)
 * 샤딩 AOP 와 트랜잭션은 통합 테스트(NotificationIntegrationTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    MemberRepository memberRepository;

    @InjectMocks
    NotificationService notificationService;

    private static final long LOGIN_ID = 1L;

    private static Member member(long id, String nickname) {
        Member member = Member.create(nickname, nickname + "@test.com", "pw", MemberRole.FAN);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Notification notification(long id, long receiverId, long actorId, NotificationType type, long targetId) {
        Notification notification = Notification.create("event-" + id, receiverId, actorId, type, targetId);
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 11, 25, 10, 30));
        return notification;
    }

    private static MemberIdNickname idNickname(long id, String nickname) {
        return new MemberIdNickname() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getNickname() {
                return nickname;
            }
        };
    }

    private void givenLoginMember() {
        given(memberRepository.findById(LOGIN_ID)).willReturn(Optional.of(member(LOGIN_ID, "나")));
    }

    // ========================================================================================
    // findAll
    // ========================================================================================

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("알림마다 actor 닉네임으로 메시지를 만들고, 이동 경로/읽음 여부/페이지 정보를 담아 반환한다")
        void responses() {
            givenLoginMember();
            Notification like = notification(10L, LOGIN_ID, 2L, NotificationType.LIKE_POST, 100L);
            Notification write = notification(11L, LOGIN_ID, 3L, NotificationType.WRITE_POST, 200L);
            write.read();
            given(notificationRepository.findByReceiver(eq(LOGIN_ID), any(), any()))
                    .willReturn(new SliceImpl<>(List.of(like, write), PageRequest.of(0, 2), true));
            given(memberRepository.findByIdIn(anyList()))
                    .willReturn(List.of(idNickname(2L, "철수"), idNickname(3L, "영희")));

            SliceDTO<NotificationResponse> result = notificationService.findAll(LOGIN_ID, PageRequest.of(0, 2));

            assertThat(result.getContent()).hasSize(2);
            NotificationResponse first = result.getContent().get(0);
            assertThat(first.getId()).isEqualTo(10L);
            assertThat(first.getMessage()).isEqualTo("[철수]님이 내 게시글에 좋아요를 눌렀습니다.");
            assertThat(first.getTarget()).isEqualTo("/api/posts/100");
            assertThat(first.isRead()).isFalse();
            assertThat(first.getReceiverId()).isEqualTo(LOGIN_ID);
            assertThat(first.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 30));

            NotificationResponse second = result.getContent().get(1);
            assertThat(second.getMessage()).isEqualTo("[영희]님이 게시글을 작성했습니다.");
            assertThat(second.isRead()).isTrue();

            assertThat(result.isHasNext()).isTrue();
        }

        @Test
        @DisplayName("조회 기간은 29일 전 0시부터이고, 요청한 정렬과 무관하게 최신순으로 조회한다")
        void periodAndSort() {
            givenLoginMember();
            given(notificationRepository.findByReceiver(eq(LOGIN_ID), any(), any()))
                    .willReturn(new SliceImpl<>(List.of()));
            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

            notificationService.findAll(LOGIN_ID, PageRequest.of(3, 20, Sort.by("id").ascending()));

            verify(notificationRepository).findByReceiver(eq(LOGIN_ID), from.capture(), pageable.capture());
            assertThat(from.getValue()).isEqualTo(LocalDate.now().minusDays(29).atStartOfDay());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
            assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
        }

        @Test
        @DisplayName("같은 actor 의 알림이 여러 개여도 닉네임을 한 번에 조회해서 매핑한다")
        void sameActor() {
            givenLoginMember();
            given(notificationRepository.findByReceiver(eq(LOGIN_ID), any(), any()))
                    .willReturn(new SliceImpl<>(List.of(
                            notification(10L, LOGIN_ID, 2L, NotificationType.LIKE_POST, 100L),
                            notification(11L, LOGIN_ID, 2L, NotificationType.LIKE_POST, 101L))));
            given(memberRepository.findByIdIn(anyList())).willReturn(List.of(idNickname(2L, "철수")));

            SliceDTO<NotificationResponse> result = notificationService.findAll(LOGIN_ID, PageRequest.of(0, 10));

            assertThat(result.getContent()).extracting(NotificationResponse::getMessage)
                    .containsOnly("[철수]님이 내 게시글에 좋아요를 눌렀습니다.");
            verify(memberRepository, times(1)).findByIdIn(anyList());
        }

        @Test
        @DisplayName("[현재 동작] actor 회원을 찾을 수 없으면 메시지의 닉네임 자리에 null 이 들어간다")
        void actorNotFound() {
            givenLoginMember();
            given(notificationRepository.findByReceiver(eq(LOGIN_ID), any(), any()))
                    .willReturn(new SliceImpl<>(List.of(
                            notification(10L, LOGIN_ID, 99L, NotificationType.FOLLOW_MEMBER, 5L))));
            given(memberRepository.findByIdIn(anyList())).willReturn(List.of());

            SliceDTO<NotificationResponse> result = notificationService.findAll(LOGIN_ID, PageRequest.of(0, 10));

            assertThat(result.getContent().getFirst().getMessage()).isEqualTo("[null]님이 회원님을 팔로우했습니다.");
        }

        @Test
        @DisplayName("로그인 회원이 없으면 MEMBER_NOT_FOUND 예외를 던지고 알림을 조회하지 않는다")
        void memberNotFound() {
            given(memberRepository.findById(LOGIN_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.findAll(LOGIN_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
            verifyNoInteractions(notificationRepository);
        }
    }

    // ========================================================================================
    // readNotification / deleteNotification
    // ========================================================================================

    @Nested
    @DisplayName("readNotification / deleteNotification")
    class ReadAndDelete {

        @Test
        @DisplayName("본인 알림은 읽음 처리된다")
        void readOwn() {
            givenLoginMember();
            Notification own = notification(10L, LOGIN_ID, 2L, NotificationType.LIKE_POST, 100L);
            given(notificationRepository.findById(10L)).willReturn(Optional.of(own));

            notificationService.readNotification(LOGIN_ID, 10L);

            assertThat(own.isRead()).isTrue();
        }

        @Test
        @DisplayName("다른 회원의 알림은 읽음 처리되지 않는다 (예외 없이 무시)")
        void readOthers() {
            givenLoginMember();
            Notification others = notification(10L, 2L, 3L, NotificationType.LIKE_POST, 100L);
            given(notificationRepository.findById(10L)).willReturn(Optional.of(others));

            notificationService.readNotification(LOGIN_ID, 10L);

            assertThat(others.isRead()).isFalse();
        }

        @Test
        @DisplayName("본인 알림은 삭제된다")
        void deleteOwn() {
            givenLoginMember();
            Notification own = notification(10L, LOGIN_ID, 2L, NotificationType.LIKE_POST, 100L);
            given(notificationRepository.findById(10L)).willReturn(Optional.of(own));

            notificationService.deleteNotification(LOGIN_ID, 10L);

            verify(notificationRepository).delete(own);
        }

        @Test
        @DisplayName("다른 회원의 알림은 삭제되지 않는다 (예외 없이 무시)")
        void deleteOthers() {
            givenLoginMember();
            Notification others = notification(10L, 2L, 3L, NotificationType.LIKE_POST, 100L);
            given(notificationRepository.findById(10L)).willReturn(Optional.of(others));

            notificationService.deleteNotification(LOGIN_ID, 10L);

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("없는 알림이면 아무 일도 일어나지 않는다")
        void notificationNotFound() {
            givenLoginMember();
            given(notificationRepository.findById(10L)).willReturn(Optional.empty());

            notificationService.readNotification(LOGIN_ID, 10L);
            notificationService.deleteNotification(LOGIN_ID, 10L);

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("로그인 회원이 없으면 MEMBER_NOT_FOUND 예외를 던진다")
        void memberNotFound() {
            given(memberRepository.findById(LOGIN_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.readNotification(LOGIN_ID, 10L))
                    .isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> notificationService.deleteNotification(LOGIN_ID, 10L))
                    .isInstanceOf(ApiException.class);
            verifyNoInteractions(notificationRepository);
        }
    }
}
