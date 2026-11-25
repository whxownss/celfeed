package com.xowns.celfeed.server.social.service;

import com.xowns.celfeed.common.event.LikePostEvent;
import com.xowns.celfeed.common.event.SocialEvent;
import com.xowns.celfeed.common.event.WritePostEvent;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.domain.social.dto.PostDetail;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import com.xowns.celfeed.domain.social.repository.LikeRepository;
import com.xowns.celfeed.domain.social.repository.MemberRepository;
import com.xowns.celfeed.domain.social.repository.PostQueryRepository;
import com.xowns.celfeed.domain.social.repository.PostRepository;
import com.xowns.celfeed.server.social.dto.request.PostRequest;
import com.xowns.celfeed.server.social.dto.response.PostDetailResponse;
import com.xowns.celfeed.server.social.outbox.OutboxEventRecorder;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.xowns.celfeed.server.social.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    PostRepository postRepository;

    @Mock
    PostQueryRepository postQueryRepository;

    @Mock
    LikeRepository likeRepository;

    @Mock
    MemberRepository memberRepository;

    @Mock
    OutboxEventRecorder outboxEventRecorder;

    @InjectMocks
    PostService postService;

    private final Member writer = member(1L, "writer");
    private final Member fan = member(2L, "fan");

    private static PostRequest postRequest(String content) {
        return fromJson("{\"content\": \"%s\"}".formatted(content), PostRequest.class);
    }

    private static void assertErrorCode(Throwable e, ErrorCode expected) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getErrorCode()).isEqualTo(expected);
    }

    @Nested
    @DisplayName("게시글 작성/조회/수정/삭제")
    class PostCrud {

        @Test
        @DisplayName("작성하면 저장하고, WritePostEvent(postId, 작성자 id)를 Outbox 에 기록한다")
        void write() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(writer));
            given(postRepository.save(any(Post.class))).willReturn(post(10L, writer, "내용입니다"));

            Long postId = postService.write(1L, postRequest("내용입니다"));

            assertThat(postId).isEqualTo(10L);
            ArgumentCaptor<SocialEvent> event = ArgumentCaptor.forClass(SocialEvent.class);
            verify(outboxEventRecorder).record(event.capture());
            WritePostEvent recorded = (WritePostEvent) event.getValue();
            assertThat(recorded.getPostId()).isEqualTo(10L);
            assertThat(recorded.getActorId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("작성자가 없으면 MEMBER_NOT_FOUND 이고 이벤트를 기록하지 않는다")
        void writeMemberNotFound() {
            given(memberRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.write(1L, postRequest("내용입니다")))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.MEMBER_NOT_FOUND));
            verifyNoInteractions(outboxEventRecorder);
        }

        @Test
        @DisplayName("상세 조회 : 결과를 응답으로 변환하고, 없으면 POST_NOT_FOUND")
        void findOneDetail() {
            LocalDateTime now = LocalDateTime.now();
            given(postQueryRepository.findByDetail(2L, 10L, false))
                    .willReturn(Optional.of(new PostDetail(10L, "내용", "writer", 3L, true, now, now)));
            given(postQueryRepository.findByDetail(2L, 11L, false)).willReturn(Optional.empty());

            PostDetailResponse response = postService.findOneDetail(2L, 10L);

            assertThat(response.getLikeCount()).isEqualTo(3L);
            assertThat(response.isLiked()).isTrue();
            assertThatThrownBy(() -> postService.findOneDetail(2L, 11L))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.POST_NOT_FOUND));
        }

        @Test
        @DisplayName("본인 글은 수정/삭제(soft delete)할 수 있다")
        void updateAndDelete() {
            Post post = post(10L, writer, "before");
            given(memberRepository.findById(1L)).willReturn(Optional.of(writer));
            given(postRepository.findByIdAndMember(10L, writer)).willReturn(Optional.of(post));

            postService.updatePost(1L, 10L, postRequest("after content"));
            assertThat(post.getContent()).isEqualTo("after content");

            postService.deletePost(1L, 10L);
            assertThat(post.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("다른 사람의 글은 수정/삭제할 수 없다 (POST_NOT_FOUND)")
        void updateOthersPost() {
            given(memberRepository.findById(2L)).willReturn(Optional.of(fan));
            given(postRepository.findByIdAndMember(10L, fan)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.updatePost(2L, 10L, postRequest("after content")))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.POST_NOT_FOUND));
            assertThatThrownBy(() -> postService.deletePost(2L, 10L))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.POST_NOT_FOUND));
        }

        @Test
        @DisplayName("목록 조회 : 정렬이 없으면 최신순(createdAt desc)을 기본으로 적용한다")
        void findAllDefaultSort() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(writer));
            given(postRepository.findByMember(eq(writer), eq(false), any())).willReturn(new SliceImpl<>(List.of()));

            postService.findAll(1L, PageRequest.of(1, 5));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(postRepository).findByMember(eq(writer), eq(false), pageable.capture());
            assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("목록 조회 : 요청에 정렬이 있으면 그대로 사용한다")
        void findAllKeepsRequestedSort() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(writer));
            given(postRepository.findByMember(eq(writer), eq(false), any())).willReturn(new SliceImpl<>(List.of()));
            PageRequest requested = PageRequest.of(0, 5, Sort.by("id").ascending());

            postService.findAll(1L, requested);

            verify(postRepository).findByMember(writer, false, requested);
        }
    }

    @Nested
    @DisplayName("좋아요")
    class Likes {

        private final Post writersPost = post(10L, writer, "내용");

        @Test
        @DisplayName("남의 글에 좋아요하면 LikePostEvent(likeId, postId, 받는 사람=작성자, 누른 사람)를 기록한다")
        void likeOthersPost() {
            given(memberRepository.findById(2L)).willReturn(Optional.of(fan));
            given(postRepository.findByIdAndIsDeleted(10L, false)).willReturn(Optional.of(writersPost));
            given(likeRepository.findByPostAndMember(writersPost, fan)).willReturn(Optional.empty());
            given(likeRepository.save(any(Like.class))).willReturn(like(100L, writersPost, fan));

            Long likeId = postService.likePost(2L, 10L);

            assertThat(likeId).isEqualTo(100L);
            ArgumentCaptor<SocialEvent> event = ArgumentCaptor.forClass(SocialEvent.class);
            verify(outboxEventRecorder).record(event.capture());
            LikePostEvent recorded = (LikePostEvent) event.getValue();
            assertThat(recorded.getLikeId()).isEqualTo(100L);
            assertThat(recorded.getPostId()).isEqualTo(10L);
            assertThat(recorded.getReceiverId()).isEqualTo(1L);
            assertThat(recorded.getActorId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("자기 글에 좋아요하면 저장은 하지만 알림 이벤트는 기록하지 않는다")
        void likeOwnPost() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(writer));
            given(postRepository.findByIdAndIsDeleted(10L, false)).willReturn(Optional.of(writersPost));
            given(likeRepository.findByPostAndMember(writersPost, writer)).willReturn(Optional.empty());
            given(likeRepository.save(any(Like.class))).willReturn(like(100L, writersPost, writer));

            assertThat(postService.likePost(1L, 10L)).isEqualTo(100L);
            verifyNoInteractions(outboxEventRecorder);
        }

        @Test
        @DisplayName("이미 좋아요했으면 기존 id 를 반환하고 저장/이벤트 기록을 하지 않는다")
        void alreadyLiked() {
            given(memberRepository.findById(2L)).willReturn(Optional.of(fan));
            given(postRepository.findByIdAndIsDeleted(10L, false)).willReturn(Optional.of(writersPost));
            given(likeRepository.findByPostAndMember(writersPost, fan))
                    .willReturn(Optional.of(like(100L, writersPost, fan)));

            assertThat(postService.likePost(2L, 10L)).isEqualTo(100L);
            verify(likeRepository, never()).save(any());
            verifyNoInteractions(outboxEventRecorder);
        }

        @Test
        @DisplayName("삭제됐거나 없는 글에는 좋아요할 수 없다 (POST_NOT_FOUND)")
        void likeDeletedPost() {
            given(memberRepository.findById(2L)).willReturn(Optional.of(fan));
            given(postRepository.findByIdAndIsDeleted(10L, false)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.likePost(2L, 10L))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.POST_NOT_FOUND));
            verifyNoInteractions(outboxEventRecorder);
        }

        @Test
        @DisplayName("좋아요 취소 : 좋아요가 있으면 삭제하고, 없으면 아무 일도 없다")
        void unlike() {
            Like like = like(100L, writersPost, fan);
            given(memberRepository.findById(2L)).willReturn(Optional.of(fan));
            given(postRepository.findByIdAndIsDeleted(10L, false)).willReturn(Optional.of(writersPost));
            given(likeRepository.findByPostAndMember(writersPost, fan))
                    .willReturn(Optional.of(like))
                    .willReturn(Optional.empty());

            postService.unlikePost(2L, 10L);
            postService.unlikePost(2L, 10L);

            verify(likeRepository, times(1)).delete(like);
        }
    }
}
