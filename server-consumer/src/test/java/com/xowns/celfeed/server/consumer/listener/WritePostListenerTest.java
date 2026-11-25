package com.xowns.celfeed.server.consumer.listener;

import com.xowns.celfeed.common.event.KafkaTopic;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.FollowQueryRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import com.xowns.celfeed.server.consumer.cache.IdempotencyCache;
import com.xowns.celfeed.server.consumer.exception.NonRetryableConsumerException;
import com.xowns.celfeed.server.consumer.listener.event.WritePostFanoutEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static com.xowns.celfeed.server.consumer.listener.ListenerTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * write-post 리스너 : 게시글 이벤트를 팔로워 구간(fanout) 메시지로 나눠 보낸다 (Kafka 트랜잭션 안에서 실행)
 * 실제 컨테이너는 Kafka 트랜잭션을 쓰므로, 테스트에서는 트랜잭션 동기화를 직접 열어 커밋/롤백 콜백을 흉내 낸다.
 */
class WritePostListenerTest {

    private static final String TYPE = "write-post";
    private static final int BATCH_SIZE = 3;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final FollowQueryRepository followQueryRepository = mock(FollowQueryRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, WritePostFanoutEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final IdempotencyCache idempotencyCache = mock(IdempotencyCache.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private final WritePostListener listener = new WritePostListener(
            OBJECT_MAPPER, postRepository, followQueryRepository, kafkaTemplate, idempotencyCache);

    // 작성자(1)가 글(10) 작성
    private final WritePostEvent event = new WritePostEvent(10L, 1L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "batchSize", BATCH_SIZE);
        given(postRepository.findGraphById(10L)).willReturn(Optional.of(Post.create(member(1L, "celeb"), "content")));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void consume() {
        listener.listener(record(KafkaTopic.WRITE_POST, event), ack);
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private List<WritePostFanoutEvent> sentFanouts() {
        ArgumentCaptor<WritePostFanoutEvent> captor = ArgumentCaptor.forClass(WritePostFanoutEvent.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq(KafkaTopic.WRITE_POST_FANOUT), anyString(), captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("경계값으로 팔로워 구간을 나눠 fanout 메시지를 보낸다 : (null, b1], (b1, b2], (b2, null]")
    void fanoutSegments() {
        given(followQueryRepository.findBoundaryFollowerId(1L, null, BATCH_SIZE)).willReturn(103L);
        given(followQueryRepository.findBoundaryFollowerId(1L, 103L, BATCH_SIZE)).willReturn(106L);
        given(followQueryRepository.findBoundaryFollowerId(1L, 106L, BATCH_SIZE)).willReturn(null);

        consume();

        List<WritePostFanoutEvent> fanouts = sentFanouts();
        assertThat(fanouts)
                .extracting(WritePostFanoutEvent::startFollowerId, WritePostFanoutEvent::endFollowerId)
                .containsExactly(tuple(null, 103L), tuple(103L, 106L), tuple(106L, null));
        assertThat(fanouts).allSatisfy(fanout -> {
            assertThat(fanout.eventId()).isEqualTo(event.getEventId());
            assertThat(fanout.postId()).isEqualTo(10L);
            assertThat(fanout.authorId()).isEqualTo(1L);
            assertThat(fanout.authorNickname()).isEqualTo("celeb");
        });
        // 메시지 키 = eventId:구간 시작
        verify(kafkaTemplate).send(KafkaTopic.WRITE_POST_FANOUT, event.getEventId() + ":null", fanouts.get(0));
        verify(kafkaTemplate).send(KafkaTopic.WRITE_POST_FANOUT, event.getEventId() + ":103", fanouts.get(1));
        verify(kafkaTemplate).send(KafkaTopic.WRITE_POST_FANOUT, event.getEventId() + ":106", fanouts.get(2));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("팔로워가 batchSize 이하면 (null, null] 구간 하나만 보낸다")
    void singleSegment() {
        given(followQueryRepository.findBoundaryFollowerId(1L, null, BATCH_SIZE)).willReturn(null);

        consume();

        assertThat(sentFanouts())
                .extracting(WritePostFanoutEvent::startFollowerId, WritePostFanoutEvent::endFollowerId)
                .containsExactly(tuple(null, null));
    }

    @Test
    @DisplayName("처리 완료 마크는 Kafka 트랜잭션이 커밋된 뒤에만 남긴다 (롤백되면 남기지 않음)")
    void markProcessedAfterCommit() {
        given(followQueryRepository.findBoundaryFollowerId(anyLong(), any(), anyInt())).willReturn(null);

        consume();
        verify(idempotencyCache, never()).markProcessed(any(), any()); // 아직 커밋 전

        commit();
        verify(idempotencyCache).markProcessed(TYPE, event.getEventId());
    }

    @Test
    @DisplayName("이미 fanout 을 끝낸 이벤트면 다시 보내지 않고 ack 한다 (Producer 중복 발행 대비)")
    void alreadyProcessed() {
        given(idempotencyCache.isProcessed(TYPE, event.getEventId())).willReturn(true);

        consume();

        verifyNoInteractions(kafkaTemplate, postRepository);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("게시글이 없으면(삭제 등) fanout 하지 않는다")
    void postNotFound() {
        given(postRepository.findGraphById(10L)).willReturn(Optional.empty());

        consume();

        verifyNoInteractions(kafkaTemplate);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("필수 값이 빠지면 재시도 불가 예외를 던진다")
    void missingField() {
        assertThatThrownBy(() -> listener.listener(record(KafkaTopic.WRITE_POST, "{\"eventId\":\"e1\"}"), ack))
                .isInstanceOf(NonRetryableConsumerException.class)
                .hasMessageContaining("필수 값 누락");
    }
}
