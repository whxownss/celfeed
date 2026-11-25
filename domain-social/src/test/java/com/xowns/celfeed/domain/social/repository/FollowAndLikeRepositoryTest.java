package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.Follow;
import com.xowns.celfeed.domain.social.entity.Like;
import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowAndLikeRepositoryTest extends SocialRepositoryTestSupport {

    @Autowired
    FollowRepository followRepository;

    @Autowired
    LikeRepository likeRepository;

    private Member managed(Member member) {
        return em.find(Member.class, member.getId());
    }

    @Nested
    @DisplayName("FollowRepository")
    class FollowTest {

        @Test
        @DisplayName("팔로우 관계(from → to)를 조회할 수 있고, 방향이 반대면 조회되지 않는다")
        void findByFromMemberAndToMember() {
            Member fan = member();
            Member celeb = member();
            Follow follow = follow(fan, celeb);
            flushAndClear();

            assertThat(followRepository.findByFromMemberAndToMember(managed(fan), managed(celeb)))
                    .get().extracting(Follow::getId).isEqualTo(follow.getId());
            assertThat(followRepository.findByFromMemberAndToMember(managed(celeb), managed(fan))).isEmpty();
        }

        @Test
        @DisplayName("팔로잉 목록은 대상 회원(toMember)을, 팔로워 목록은 팔로워(fromMember)를 fetch join 해서 조회한다")
        void followingAndFollowers() {
            Member me = member();
            Member celeb1 = member();
            Member celeb2 = member();
            Member fan = member();
            follow(me, celeb1);
            follow(me, celeb2);
            follow(fan, me);
            flushAndClear();

            PageRequest page = PageRequest.of(0, 10, Sort.by("id"));
            Slice<Follow> following = followRepository.findByFromMember(managed(me), page);
            Slice<Follow> followers = followRepository.findByToMember(managed(me), page);
            em.clear();

            assertThat(following.getContent()).extracting(f -> f.getToMember().getId())
                    .containsExactly(celeb1.getId(), celeb2.getId());
            assertThat(following.getContent()).allSatisfy(f ->
                    assertThat(Hibernate.isInitialized(f.getToMember())).isTrue());

            assertThat(followers.getContent()).extracting(f -> f.getFromMember().getId())
                    .containsExactly(fan.getId());
            assertThat(followers.getContent()).allSatisfy(f ->
                    assertThat(Hibernate.isInitialized(f.getFromMember())).isTrue());
        }

        @Test
        @DisplayName("findGraphById 는 팔로워(fromMember)를 함께 조회한다 (알림 메시지의 닉네임용)")
        void findGraphById() {
            Follow follow = follow(member(), member());
            flushAndClear();

            Follow found = followRepository.findGraphById(follow.getId()).orElseThrow();

            assertThat(Hibernate.isInitialized(found.getFromMember())).isTrue();
        }

        @Test
        @DisplayName("같은 팔로우 관계를 두 번 저장할 수 없다 (uk_follow_from_to)")
        void duplicateFollow() {
            Member fan = member();
            Member celeb = member();
            followRepository.saveAndFlush(Follow.create(fan, celeb));

            assertThatThrownBy(() -> followRepository.saveAndFlush(Follow.create(fan, celeb)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("LikeRepository")
    class LikeTest {

        @Test
        @DisplayName("게시글 + 회원으로 좋아요를 조회할 수 있다")
        void findByPostAndMember() {
            Member writer = member();
            Member fan = member();
            Post post = post(writer, "내용");
            Like like = like(post, fan);
            flushAndClear();

            Post managedPost = em.find(Post.class, post.getId());
            assertThat(likeRepository.findByPostAndMember(managedPost, managed(fan)))
                    .get().extracting(Like::getId).isEqualTo(like.getId());
            assertThat(likeRepository.findByPostAndMember(managedPost, managed(writer))).isEmpty();
        }

        @Test
        @DisplayName("findGraphById 는 게시글, 게시글 작성자, 좋아요 누른 회원을 함께 조회한다")
        void findGraphById() {
            Post post = post(member(), "내용");
            Like like = like(post, member());
            flushAndClear();

            Like found = likeRepository.findGraphById(like.getId()).orElseThrow();

            assertThat(Hibernate.isInitialized(found.getPost())).isTrue();
            assertThat(Hibernate.isInitialized(found.getPost().getMember())).isTrue();
            assertThat(Hibernate.isInitialized(found.getMember())).isTrue();
        }

        @Test
        @DisplayName("같은 게시글에 같은 회원이 좋아요를 두 번 저장할 수 없다 (uk_likes_post_member)")
        void duplicateLike() {
            Post post = post(member(), "내용");
            Member fan = member();
            likeRepository.saveAndFlush(Like.create(post, fan));

            assertThatThrownBy(() -> likeRepository.saveAndFlush(Like.create(post, fan)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
