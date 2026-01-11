package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.Post;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("select p from Post p join fetch p.member where p.isDeleted = :isDeleted")
    Optional<Post> findById(Long postId, @Param("isDeleted") boolean isDeleted);

    Optional<Post> findByIdAndMember(Long postId, Member member);

    @Query("select p from Post p join fetch p.member where p.member = :member and p.isDeleted = :isDeleted")
    Slice<Post> findAllByMember(@Param("member") Member member, @Param("isDeleted") boolean isDeleted, Pageable pageable);
}
