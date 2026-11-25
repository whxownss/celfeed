package com.xowns.celfeed.server.social.outbox;

import com.xowns.celfeed.common.event.FollowMemberEvent;
import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.domain.social.entity.OutboxEvent;
import com.xowns.celfeed.domain.social.entity.OutboxStatus;
import com.xowns.celfeed.domain.social.repository.OutboxRepository;
import com.xowns.celfeed.server.social.outbox.event.OutboxCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventRecorderTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OutboxEventRecorder recorder = new OutboxEventRecorder(objectMapper, outboxRepository, publisher);

    @BeforeEach
    void setUp() {
        given(outboxRepository.save(any(OutboxEvent.class))).willAnswer(invocation -> {
            OutboxEvent outbox = invocation.getArgument(0);
            ReflectionTestUtils.setField(outbox, "id", 77L);
            return outbox;
        });
    }

    private OutboxEvent savedOutbox() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("게시글 작성 : 타입 WRITE_POST, aggregate = postId, 파티션 키 = postId")
    void writePost() {
        WritePostEvent event = new WritePostEvent(10L, 1L);

        recorder.record(event);

        OutboxEvent outbox = savedOutbox();
        assertThat(outbox.getEventId()).isEqualTo(event.getEventId());
        assertThat(outbox.getEventType()).isEqualTo("WRITE_POST");
        assertThat(outbox.getAggregateId()).isEqualTo(10L);
        assertThat(outbox.getPartitionKey()).isEqualTo("10");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("좋아요 : 타입 LIKE_POST, aggregate = likeId, 파티션 키 = postId:actorId (인기 글 핫파티션 방지)")
    void likePost() {
        LikePostEvent event = new LikePostEvent(100L, 10L, 1L, 2L);

        recorder.record(event);

        OutboxEvent outbox = savedOutbox();
        assertThat(outbox.getEventType()).isEqualTo("LIKE_POST");
        assertThat(outbox.getAggregateId()).isEqualTo(100L);
        assertThat(outbox.getPartitionKey()).isEqualTo("10:2");
    }

    @Test
    @DisplayName("팔로우 : 타입 FOLLOW_MEMBER, aggregate = followId, 파티션 키 = toId:fromId")
    void followMember() {
        FollowMemberEvent event = new FollowMemberEvent(5L, 1L, 2L);

        recorder.record(event);

        OutboxEvent outbox = savedOutbox();
        assertThat(outbox.getEventType()).isEqualTo("FOLLOW_MEMBER");
        assertThat(outbox.getAggregateId()).isEqualTo(5L);
        assertThat(outbox.getPartitionKey()).isEqualTo("2:1");
    }

    @Test
    @DisplayName("저장 후 OutboxCreatedEvent(저장된 outbox id)를 발행한다 → 커밋 후 즉시 발행 트리거")
    void publishCreatedEvent() {
        recorder.record(new WritePostEvent(10L, 1L));

        verify(publisher).publishEvent(new OutboxCreatedEvent(77L));
    }

    /**
     * common-event 직렬화 계약 : social 이 만든 payload 를 consumer 가 같은 이벤트 클래스로 읽을 수 있어야 한다.
     * (consumer 는 모든 필드가 null 이 아니어야 처리한다)
     */
    @Test
    @DisplayName("payload 는 같은 이벤트 클래스로 역직렬화하면 모든 필드가 그대로 복원된다")
    void payloadRoundTrip() {
        LikePostEvent like = new LikePostEvent(100L, 10L, 1L, 2L);
        recorder.record(like);

        LikePostEvent restored = objectMapper.readValue(savedOutbox().getPayload(), LikePostEvent.class);

        assertThat(restored).usingRecursiveComparison().isEqualTo(like);
        assertThat(restored.getEventId()).isNotNull();
        assertThat(restored.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("작성/팔로우 이벤트 payload 도 그대로 복원된다")
    void payloadRoundTripOthers() {
        WritePostEvent write = new WritePostEvent(10L, 1L);
        FollowMemberEvent follow = new FollowMemberEvent(5L, 1L, 2L);

        recorder.record(write);
        recorder.record(follow);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(objectMapper.readValue(captor.getAllValues().get(0).getPayload(), WritePostEvent.class))
                .usingRecursiveComparison().isEqualTo(write);
        assertThat(objectMapper.readValue(captor.getAllValues().get(1).getPayload(), FollowMemberEvent.class))
                .usingRecursiveComparison().isEqualTo(follow);
    }
}
