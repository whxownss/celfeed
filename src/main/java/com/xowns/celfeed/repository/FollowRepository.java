package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Follow;
import com.xowns.celfeed.domain.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFromMemberAndToMember(Member fromMember, Member toMember);

    @Query("select f from Follow f join fetch f.toMember where f.fromMember = :fromMember")
    Slice<Follow> findByFromMember(@Param("fromMember") Member fromMember, Pageable pageable);

    @Query("select f from Follow f join fetch f.fromMember where f.toMember = :toMember")
    Slice<Follow> findByToMember(@Param("toMember") Member toMember, Pageable pageable);
}
