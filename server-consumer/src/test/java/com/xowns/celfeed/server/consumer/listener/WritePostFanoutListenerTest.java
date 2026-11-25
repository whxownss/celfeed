package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.notification.entity.Notification;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.exception.RetryableConsumerException;
import com.xowns.celfeed.server.consumer.listener.event.WritePostFanoutEvent;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.sender.NotificationSenderResolver;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.xowns.celfeed.server.consumer.listener.ListenerTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * write-post-fanout 리스너 : 팔로워 구간 (start, end] 의 팔로워에게 샤드별로 알림 저장 + 전송
 */
class WritePostFanoutListenerTest {

    private static final String TYPE = "write-post-fanout";

    private final FollowQueryRepository followQueryRepository = mock(FollowQueryRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationSenderResolver senderResolver = mock(NotificationSenderResolver.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final IdempotencyCache idempotencyCache = mock(IdempotencyCache.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private final WritePostFanoutListener listener = new WritePostFanoutListener(
            OBJECT_MAPPER, followQueryRepository, notificationService, senderResolver, idempotencyCache);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "mod", 2);
        given(senderResolver.resolve()).willReturn(sender);
        given(idempotencyCache.tryLock(eq(TYPE), anyString())).willReturn(ACQUIRED);
    }

    private static WritePostFanoutEvent fanout(Long start, Long end) {
        return new WritePostFanoutEvent("event-1", 10L, 1L, "celeb", start, end);
    }

    private void consume(WritePostFanoutEvent event) {
        listener.listener(record(KafkaTopic.WRITE_POST_FANOUT, event), ack);
    }

    /**
     * 현재 동작 기록 (중요 — 버그로 보임)
     * WritePostListener 는 첫 구간의 start 와 마지막 구간의 end 를 항상 null 로 보낸다. (domain-social FollowQueryRepositoryTest 참고)
     * 그런데 parse() 는 startFollowerId / endFollowerId 가 null 이면 "필수 값 누락" 으로 재시도 불가 예외를 던진다.
     * → 첫/마지막 구간 팔로워는 알림을 받지 못하고 메시지는 DLT 로 간다.
     * → 팔로워가 batch-size(운영 1000) 이하면 구간이 (null, null] 하나뿐이라 게시글 알림이 아무에게도 가지 않는다.
     * idempotencyKey() 는 null 을 "first" / "last" 로 처리하도록 만들어져 있어서, parse 의 null 검사가 의도와 다른 것으로 보인다.
     */
    @ParameterizedTest(name = "구간 ({0}, {1}]")
    @CsvSource(value = {"null, 103", "106, null", "null, null"}, nullValues = "null")
    @DisplayName("[현재 동작] 첫/마지막 구간(start 또는 end 가 null)은 필수 값 누락으로 처리되지 않는다")
    void firstAndLastSegmentsAreRejected(Long start, Long end) {
        assertThatThrownBy(() -> consume(fanout(start, end)))
                .isInstanceOf(NonRetryableConsumerException.class)
                .hasMessageContaining("필수 값 누락");

        verifyNoInteractions(followQueryRepository, notificationService);
        verify(ack, never()).acknowledge();
    }

    @Nested
    @DisplayName("중간 구간 처리")
    class MiddleSegment {

        private final WritePostFanoutEvent event = fanout(103L, 106L);
        private final String idempotencyKey = "event-1:103-106";

        @Test
        @DisplayName("구간 팔로워를 샤드(팔로워 id % 2)별로 나눠 저장하고, 새로 저장된 알림만 전송한다")
        void saveByShard() {
            given(followQueryRepository.findFollowerIds(1L, 103L, 106L)).willReturn(List.of(104L, 105L, 106L));
            given(notificationService.saveWritePostNotification(eq(0L), eq(List.of(104L, 106L)), eq(1L), eq(10L), eq("event-1")))
                    .willReturn(List.of(notification(1L, "event-1", 104L, NotificationType.WRITE_POST))); // 106 은 이미 저장돼 있었음
            given(notificationService.saveWritePostNotification(eq(1L), eq(List.of(105L)), eq(1L), eq(10L), eq("event-1")))
                    .willReturn(List.of(notification(2L, "event-1", 105L, NotificationType.WRITE_POST)));

            consume(event);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<NotificationDto>> sent = ArgumentCaptor.forClass(List.class);
            verify(sender, times(2)).send(sent.capture());
            assertThat(sent.getAllValues()).flatExtracting(list -> list)
                    .extracting(NotificationDto::getReceiverId)
                    .containsExactlyInAnyOrder(104L, 105L);
            assertThat(sent.getAllValues().getFirst().getFirst().getMessage()).isEqualTo("[celeb]님이 게시글을 작성했습니다.");

            verify(idempotencyCache).markProcessed(TYPE, idempotencyKey);
            verify(idempotencyCache).releaseLock(ACQUIRED);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("멱등 키는 eventId + 구간(start-end) 이라, 같은 게시글이라도 구간마다 따로 처리된다")
        void idempotencyKeyPerSegment() {
            given(idempotencyCache.isProcessed(TYPE, idempotencyKey)).willReturn(true);

            consume(event);

            verifyNoInteractions(followQueryRepository, notificationService);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("그 사이 모두 언팔로우해서 팔로워가 없으면 저장 없이 완료 처리한다")
        void noFollowers() {
            given(followQueryRepository.findFollowerIds(1L, 103L, 106L)).willReturn(List.of());

            consume(event);

            verifyNoInteractions(notificationService);
            verify(idempotencyCache).markProcessed(TYPE, idempotencyKey);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("다른 consumer 가 같은 구간을 처리 중이면 재시도 가능 예외")
        void heldByOther() {
            given(idempotencyCache.tryLock(TYPE, idempotencyKey)).willReturn(HELD_BY_OTHER);

            assertThatThrownBy(() -> consume(event)).isInstanceOf(RetryableConsumerException.class);
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("제약 위반은 재시도 불가 예외 (중복은 SQL 에서 이미 무시되므로 데이터 문제)")
        void constraintViolation() {
            given(followQueryRepository.findFollowerIds(1L, 103L, 106L)).willReturn(List.of(104L));
            given(notificationService.saveWritePostNotification(anyLong(), anyList(), anyLong(), anyLong(), anyString()))
                    .willThrow(otherConstraint());

            assertThatThrownBy(() -> consume(event)).isInstanceOf(NonRetryableConsumerException.class);
            verify(idempotencyCache, never()).markProcessed(any(), any());
            verify(idempotencyCache).releaseLock(ACQUIRED);
        }

        @Test
        @DisplayName("한 샤드 저장이 일시 오류로 실패하면 완료 마크 없이 예외를 던져 구간 전체를 재시도하게 한다")
        void partialFailureRetries() {
            given(followQueryRepository.findFollowerIds(1L, 103L, 106L)).willReturn(List.of(104L, 105L));
            given(notificationService.saveWritePostNotification(eq(0L), anyList(), anyLong(), anyLong(), anyString()))
                    .willReturn(List.of(notification(1L, "event-1", 104L, NotificationType.WRITE_POST)));
            given(notificationService.saveWritePostNotification(eq(1L), anyList(), anyLong(), anyLong(), anyString()))
                    .willThrow(new QueryTimeoutException("timeout"));

            assertThatThrownBy(() -> consume(event)).isInstanceOf(QueryTimeoutException.class);
            verify(idempotencyCache, never()).markProcessed(any(), any());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("전송 실패는 무시하고(best-effort) 완료 처리한다")
        void sendFailureIgnored() {
            given(followQueryRepository.findFollowerIds(1L, 103L, 106L)).willReturn(List.of(104L));
            given(notificationService.saveWritePostNotification(anyLong(), anyList(), anyLong(), anyLong(), anyString()))
                    .willReturn(List.<Notification>of(notification(1L, "event-1", 104L, NotificationType.WRITE_POST)));
            doThrow(new IllegalStateException("send failed")).when(sender).send(anyList());

            consume(event);

            verify(idempotencyCache).markProcessed(TYPE, idempotencyKey);
            verify(ack).acknowledge();
        }
    }
}
