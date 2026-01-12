package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Like;
import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByPostAndMember(Post findPost, Member member);
}
