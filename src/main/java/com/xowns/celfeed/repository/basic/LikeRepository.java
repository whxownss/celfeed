package com.xowns.celfeed.repository.basic;

import com.xowns.celfeed.domain.social.basic.Like;
import com.xowns.celfeed.domain.social.basic.Member;
import com.xowns.celfeed.domain.social.basic.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByPostAndMember(Post findPost, Member member);

    @EntityGraph(attributePaths = {"post.member", "member"})
    Optional<Like> findGraphById(Long id);
}
