package com.xowns.celfeed.domain.social.repository;

import com.xowns.celfeed.domain.social.entity.Member;
import com.xowns.celfeed.domain.social.entity.Post;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryTest extends SocialRepositoryTestSupport {

    @Autowired
    PostRepository postRepository;

    @Test
    @DisplayName("저장하면 삭제되지 않은 상태(N)로 저장되고 생성/수정 시각이 채워진다")
    void save() {
        Post post = post(member(), "내용");
        flushAndClear();

        // 네이티브 쿼리에서 VARCHAR(1) 은 Character 로 반환됨
        Object isDeleted = em.createNativeQuery("SELECT is_deleted FROM post WHERE id = :id")
                .setParameter("id", post.getId())
                .getSingleResult();
        assertThat(String.valueOf(isDeleted)).isEqualTo("N");

        Post found = postRepository.findById(post.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("내용을 수정하면 수정 시각만 바뀌고 생성 시각은 그대로다")
    void updateChangesOnlyUpdatedAt() throws InterruptedException {
        Post post = post(member(), "before");
        flushAndClear();
        Post saved = postRepository.findById(post.getId()).orElseThrow();
        LocalDateTime createdAt = saved.getCreatedAt();
        LocalDateTime updatedAt = saved.getUpdatedAt();

        Thread.sleep(5);
        saved.updateContent("after");
        flushAndClear();

        Post updated = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("after");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    @DisplayName("삭제(soft delete)된 글은 findByIdAndIsDeleted(false) 로 조회되지 않는다")
    void softDelete() {
        Post post = post(member(), "내용");
        post.deletePost();
        flushAndClear();

        assertThat(postRepository.findByIdAndIsDeleted(post.getId(), false)).isEmpty();
        assertThat(postRepository.findByIdAndIsDeleted(post.getId(), true)).isPresent();
    }

    @Test
    @DisplayName("작성자로 조회하면 본인 글만 찾을 수 있다")
    void findByIdAndMember() {
        Member writer = member();
        Member other = member();
        Post post = post(writer, "내용");
        flushAndClear();

        assertThat(postRepository.findByIdAndMember(post.getId(), em.find(Member.class, writer.getId()))).isPresent();
        assertThat(postRepository.findByIdAndMember(post.getId(), em.find(Member.class, other.getId()))).isEmpty();
    }

    @Test
    @DisplayName("[현재 동작] findByIdAndMember 는 삭제된 글도 조회한다 (수정/삭제 API 에서 사용)")
    void findByIdAndMemberIncludesDeleted() {
        Member writer = member();
        Post post = post(writer, "내용");
        post.deletePost();
        flushAndClear();

        assertThat(postRepository.findByIdAndMember(post.getId(), em.find(Member.class, writer.getId()))).isPresent();
    }

    @Test
    @DisplayName("작성자의 글 목록은 삭제되지 않은 글만, 작성자를 fetch join 해서 조회한다")
    void findByMember() {
        Member writer = member();
        Post first = post(writer, "1");
        Post second = post(writer, "2");
        post(writer, "deleted").deletePost();
        post(member(), "다른 작성자");
        flushAndClear();

        Slice<Post> result = postRepository.findByMember(
                em.find(Member.class, writer.getId()), false, PageRequest.of(0, 10, Sort.by("id").descending()));
        em.clear(); // fetch join 여부 확인을 위해 영속성 컨텍스트 분리

        assertThat(result.getContent()).extracting(Post::getId).containsExactly(second.getId(), first.getId());
        assertThat(result.getContent()).allSatisfy(p -> assertThat(Hibernate.isInitialized(p.getMember())).isTrue());
    }

    @Test
    @DisplayName("findGraphById 는 작성자를 함께 조회한다")
    void findGraphById() {
        Post post = post(member(), "내용");
        flushAndClear();

        Post found = postRepository.findGraphById(post.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getMember())).isTrue();
    }
}
