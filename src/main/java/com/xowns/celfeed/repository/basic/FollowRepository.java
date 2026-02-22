package com.xowns.celfeed.repository.basic;

import com.xowns.celfeed.domain.basic.Follow;
import com.xowns.celfeed.domain.basic.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFromMemberAndToMember(Member fromMember, Member toMember);

    @Query("select f from Follow f join fetch f.toMember where f.fromMember = :fromMember")
    Slice<Follow> findByFromMember(@Param("fromMember") Member fromMember, Pageable pageable);

    @Query("select f from Follow f join fetch f.fromMember where f.toMember = :toMember")
    Slice<Follow> findByToMember(@Param("toMember") Member toMember, Pageable pageable);

    List<Follow> findByToMember(@Param("toMember") Member toMember);

    @Query("select f.fromMember.id from Follow f where f.toMember = :toMember")
    List<Long> findFollowerIdsByOffset(@Param("toMember") Member toMember, Pageable pageable);
}


