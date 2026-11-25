package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.exception.RetryableConsumerException;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.sender.NotificationSenderResolver;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static com.xowns.celfeed.server.consumer.listener.ListenerTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

/**
 * like-post 리스너 단위 테스트 (follow-member 도 같은 처리 흐름)
 * 흐름 : 파싱 → 1) 처리 여부 → 2) 락 → 3) 더블 체크 → 4) 좋아요 조회 → 5) 저장 → 6) 완료 마크 → 7) 전송 → ack
 */
class LikePostListenerTest {

    private static final String TYPE = "like-post";

    private final IdempotencyCache idempotencyCache = mock(IdempotencyCache.class);
    private final NotificationSenderResolver senderResolver = mock(NotificationSenderResolver.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private final LikePostListener listener = new LikePostListener(
            OBJECT_MAPPER, idempotencyCache, senderResolver, likeRepository, notificationService);

    // 게시글 작성자(1) 의 글(10)에 팬(2)이 좋아요(100)
    private final LikePostEvent event = new LikePostEvent(100L, 10L, 1L, 2L);
    private final Member writer = member(1L, "writer");
    private final Member fan = member(2L, "fan");
    private final Like like = Like.create(Post.create(writer, "content"), fan);

    @BeforeEach
    void setUp() {
        given(senderResolver.resolve()).willReturn(sender);
        given(idempotencyCache.tryLock(TYPE, event.getEventId())).willReturn(ACQUIRED);
        given(likeRepository.findGraphById(100L)).willReturn(Optional.of(like));
    }

    private void consume() {
        listener.listener(record(KafkaTopic.LIKE_POST, event), ack);
    }

    @Nested
    @DisplayName("메시지 파싱")
    class Parse {

        @Test
        @DisplayName("빈 메시지(null)는 처리하지 않고 ack 한다")
        void nullPayload() {
            listener.listener(record(KafkaTopic.LIKE_POST, (String) null), ack);

            verify(ack).acknowledge();
            verifyNoInteractions(idempotencyCache, notificationService);
        }

        @Test
        @DisplayName("JSON 이 아니면 재시도 불가 예외(바로 DLT)를 던지고 ack 하지 않는다")
        void invalidJson() {
            assertThatThrownBy(() -> listener.listener(record(KafkaTopic.LIKE_POST, "not json"), ack))
                    .isInstanceOf(NonRetryableConsumerException.class)
                    .hasMessageContaining("역직렬화 실패");
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("필수 값이 빠지면 재시도 불가 예외를 던진다")
        void missingField() {
            String payload = "{\"eventId\":\"e1\",\"postId\":10,\"receiverId\":1,\"actorId\":2,\"occurredAt\":\"2026-11-25T10:00:00\"}";

            assertThatThrownBy(() -> listener.listener(record(KafkaTopic.LIKE_POST, payload), ack))
                    .isInstanceOf(NonRetryableConsumerException.class)
                    .hasMessageContaining("필수 값 누락");
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    @DisplayName("멱등성 / 락")
    class Idempotency {

        @Test
        @DisplayName("이미 처리된 이벤트면 락 없이 skip 하고 ack 한다")
        void alreadyProcessed() {
            given(idempotencyCache.isProcessed(TYPE, event.getEventId())).willReturn(true);

            consume();

            verify(idempotencyCache, never()).tryLock(any(), any());
            verifyNoInteractions(notificationService);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("다른 consumer 가 처리 중이면 재시도 가능 예외를 던지고 ack 하지 않는다 (처리 중 죽으면 유실되므로 skip 하지 않음)")
        void heldByOther() {
            given(idempotencyCache.tryLock(TYPE, event.getEventId())).willReturn(HELD_BY_OTHER);

            assertThatThrownBy(this::consumeLocal).isInstanceOf(RetryableConsumerException.class);
            verifyNoInteractions(notificationService);
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("락을 잡은 뒤 다시 확인했을 때 처리됐으면 저장하지 않고, 락을 해제한다")
        void doubleCheck() {
            given(idempotencyCache.isProcessed(TYPE, event.getEventId())).willReturn(false, true);

            consume();

            verifyNoInteractions(notificationService);
            verify(idempotencyCache).releaseLock(ACQUIRED);
            verify(ack).acknowledge();
        }

        private void consumeLocal() {
            consume();
        }
    }

    @Nested
    @DisplayName("알림 저장 / 전송")
    class Handle {

        @Test
        @DisplayName("수신자 id 를 샤드키로 저장하고, 완료 마크 후 전송하고, 락 해제 후 ack 한다")
        void success() {
            Notification saved = notification(1L, event.getEventId(), 1L, NotificationType.LIKE_POST);
            given(notificationService.saveLikePostNotification(1L, 2L, 10L, event.getEventId())).willReturn(saved);

            consume();

            var inOrder = inOrder(notificationService, idempotencyCache, sender, ack);
            inOrder.verify(notificationService).saveLikePostNotification(1L, 2L, 10L, event.getEventId());
            inOrder.verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
            inOrder.verify(sender).send(any(NotificationDto.class));
            inOrder.verify(idempotencyCache).releaseLock(ACQUIRED);
            inOrder.verify(ack).acknowledge();

            ArgumentCaptor<NotificationDto> dto = ArgumentCaptor.forClass(NotificationDto.class);
            verify(sender).send(dto.capture());
            assertThat(dto.getValue().getMessage()).isEqualTo("[fan]님이 내 게시글에 좋아요를 눌렀습니다.");
        }

        @Test
        @DisplayName("[현재 동작] 좋아요가 이미 취소됐으면(없으면) 저장하지 않고 ack 하지만, 완료 마크는 남기지 않는다")
        void likeNotFound() {
            given(likeRepository.findGraphById(100L)).willReturn(Optional.empty());

            consume();

            verifyNoInteractions(notificationService);
            verify(idempotencyCache, never()).markProcessed(any(), any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("DB 유니크 키 중복이면(Redis 장애/TTL 만료 후 재처리) 처리된 것으로 마크하고, 다시 전송하지 않는다")
        void duplicateKey() {
            given(notificationService.saveLikePostNotification(anyLong(), anyLong(), anyLong(), any()))
                    .willThrow(ListenerTestSupport.duplicateKey());

            consume();

            verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
            verifyNoInteractions(sender);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("다른 제약 위반은 재시도 불가 예외를 던지고, 락은 해제한다")
        void otherConstraintViolation() {
            given(notificationService.saveLikePostNotification(anyLong(), anyLong(), anyLong(), any()))
                    .willThrow(otherConstraint());

            assertThatThrownBy(LikePostListenerTest.this::consume).isInstanceOf(NonRetryableConsumerException.class);
            verify(idempotencyCache).releaseLock(ACQUIRED);
            verify(idempotencyCache, never()).markProcessed(any(), any());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("일시적인 DB 오류는 그대로 던져서 재시도하게 하고, 완료 마크를 남기지 않는다")
        void transientDbFailure() {
            given(notificationService.saveLikePostNotification(anyLong(), anyLong(), anyLong(), any()))
                    .willThrow(new QueryTimeoutException("timeout"));

            assertThatThrownBy(LikePostListenerTest.this::consume).isInstanceOf(QueryTimeoutException.class);
            verify(idempotencyCache).releaseLock(ACQUIRED);
            verify(idempotencyCache, never()).markProcessed(any(), any());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("전송이 실패해도(best-effort) 처리 완료로 보고 ack 한다")
        void sendFailureIgnored() {
            given(notificationService.saveLikePostNotification(anyLong(), anyLong(), anyLong(), any()))
                    .willReturn(notification(1L, event.getEventId(), 1L, NotificationType.LIKE_POST));
            willThrow(new IllegalStateException("send failed")).given(sender).send(any(NotificationDto.class));

            consume();

            verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
            verify(ack).acknowledge();
        }
    }
}
