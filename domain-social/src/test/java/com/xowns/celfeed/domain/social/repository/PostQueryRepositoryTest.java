package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.dto.PostDetail;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.MemberRole;
import com.xowns.celfeed.domain.social.entity.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PostQueryRepositoryTest extends SocialRepositoryTestSupport {

    @Autowired
    PostQueryRepository postQueryRepository;

    @Test
    @DisplayName("게시글 상세는 내용, 작성자 닉네임, 좋아요 수를 함께 조회한다")
    void detail() {
        Member writer = member("작성자", MemberRole.CELEB);
        Post post = post(writer, "내용");
        like(post, member());
        like(post, member());
        flushAndClear();

        PostDetail detail = postQueryRepository.findByDetail(writer.getId(), post.getId(), false).orElseThrow();

        assertThat(detail.getId()).isEqualTo(post.getId());
        assertThat(detail.getContent()).isEqualTo("내용");
        assertThat(detail.getMemberNickname()).isEqualTo("작성자");
        assertThat(detail.getLikeCount()).isEqualTo(2);
        assertThat(detail.getCreateAt()).isNotNull();
        assertThat(detail.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인한 회원이 좋아요를 눌렀으면 isLiked 가 true 다")
    void likedByLoginMember() {
        Post post = post(member(), "내용");
        Member me = member();
        like(post, member());
        like(post, me);
        flushAndClear();

        PostDetail detail = postQueryRepository.findByDetail(me.getId(), post.getId(), false).orElseThrow();

        assertThat(detail.isLiked()).isTrue();
        assertThat(detail.getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요는 있지만 로그인한 회원이 누르지 않았으면 isLiked 가 false 다")
    void notLikedByLoginMember() {
        Post post = post(member(), "내용");
        Member me = member();
        like(post, member());
        flushAndClear();

        PostDetail detail = postQueryRepository.findByDetail(me.getId(), post.getId(), false).orElseThrow();

        assertThat(detail.isLiked()).isFalse();
        assertThat(detail.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요가 하나도 없으면 좋아요 수 0, isLiked false 다")
    void noLikes() {
        Post post = post(member(), "내용");
        Member me = member();
        flushAndClear();

        PostDetail detail = postQueryRepository.findByDetail(me.getId(), post.getId(), false).orElseThrow();

        assertThat(detail.getLikeCount()).isZero();
        assertThat(detail.isLiked()).isFalse();
    }

    @Test
    @DisplayName("삭제된 글이나 없는 글은 조회되지 않는다")
    void deletedOrMissing() {
        Member writer = member();
        Post deleted = post(writer, "삭제");
        deleted.deletePost();
        flushAndClear();

        assertThat(postQueryRepository.findByDetail(writer.getId(), deleted.getId(), false)).isEmpty();
        assertThat(postQueryRepository.findByDetail(writer.getId(), Long.MAX_VALUE, false)).isEmpty();
    }
}
