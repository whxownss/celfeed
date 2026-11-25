package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.domain.notification.entity.NotificationType;
import com.xowns.celfeed.domain.social.entity.Follow;
import com.xowns.celfeed.domain.social.repository.FollowRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.dto.NotificationDto;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.sender.NotificationSender;
import com.xowns.celfeed.server.consumer.sender.NotificationSenderResolver;
import com.xowns.celfeed.server.consumer.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static com.xowns.celfeed.server.consumer.listener.ListenerTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/** follow-member 리스너 : like-post 와 같은 흐름이라 차이가 나는 부분 위주로 검증 */
class FollowMemberListenerTest {

    private static final String TYPE = "follow-member";

    private final IdempotencyCache idempotencyCache = mock(IdempotencyCache.class);
    private final NotificationSenderResolver senderResolver = mock(NotificationSenderResolver.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final FollowRepository followRepository = mock(FollowRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private final FollowMemberListener listener = new FollowMemberListener(
            OBJECT_MAPPER, idempotencyCache, senderResolver, followRepository, notificationService);

    // 팬(2)이 셀럽(1)을 팔로우(5)
    private final FollowMemberEvent event = new FollowMemberEvent(5L, 2L, 1L);

    @BeforeEach
    void setUp() {
        given(senderResolver.resolve()).willReturn(sender);
        given(idempotencyCache.tryLock(TYPE, event.getEventId())).willReturn(ACQUIRED);
        given(followRepository.findGraphById(5L))
                .willReturn(Optional.of(Follow.create(member(2L, "fan"), member(1L, "celeb"))));
    }

    private void consume() {
        listener.listener(record(KafkaTopic.FOLLOW_MEMBER, event), ack);
    }

    @Test
    @DisplayName("팔로우 당한 회원(toId)을 수신자이자 샤드키로 저장하고, 팔로워 닉네임으로 전송한다")
    void success() {
        given(notificationService.saveFollowMemberNotification(1L, 2L, 5L, event.getEventId()))
                .willReturn(notification(1L, event.getEventId(), 1L, NotificationType.FOLLOW_MEMBER));

        consume();

        verify(notificationService).saveFollowMemberNotification(1L, 2L, 5L, event.getEventId());
        verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
        ArgumentCaptor<NotificationDto> dto = ArgumentCaptor.forClass(NotificationDto.class);
        verify(sender).send(dto.capture());
        assertThat(dto.getValue().getMessage()).isEqualTo("[fan]님이 회원님을 팔로우했습니다.");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("필수 값(followId 등)이 빠지면 재시도 불가 예외를 던진다")
    void missingField() {
        String payload = "{\"eventId\":\"e1\",\"fromId\":2,\"toId\":1,\"occurredAt\":\"2026-11-25T10:00:00\"}";

        assertThatThrownBy(() -> listener.listener(record(KafkaTopic.FOLLOW_MEMBER, payload), ack))
                .isInstanceOf(NonRetryableConsumerException.class)
                .hasMessageContaining("필수 값 누락");
    }

    @Test
    @DisplayName("팔로우가 이미 취소됐으면 저장하지 않고 ack 한다")
    void followNotFound() {
        given(followRepository.findGraphById(5L)).willReturn(Optional.empty());

        consume();

        verifyNoInteractions(notificationService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("DB 유니크 키 중복이면 처리된 것으로 마크하고 다시 전송하지 않는다")
    void duplicateKey() {
        given(notificationService.saveFollowMemberNotification(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(ListenerTestSupport.duplicateKey());

        consume();

        verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
        verifyNoInteractions(sender);
        verify(ack).acknowledge();
    }
}
