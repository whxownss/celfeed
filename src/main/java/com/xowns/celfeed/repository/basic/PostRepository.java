package com.xowns.celfeed.repository.basic;

import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndIsDeleted(Long postId, boolean isDeleted);

    // isDeleted 필요없나?
    Optional<Post> findByIdAndMember(Long postId, Member member);

    @Query("select p from Post p join fetch p.member where p.member = :member and p.isDeleted = :isDeleted")
    Slice<Post> findByMember(@Param("member") Member member, @Param("isDeleted") boolean isDeleted, Pageable pageable);

    @EntityGraph(attributePaths = {"member"})
    Optional<Post> findGraphById(Long postId);
}
