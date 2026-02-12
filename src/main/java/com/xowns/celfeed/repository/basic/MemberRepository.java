package com.xowns.celfeed.repository.basic;

import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.domain.basic.MemberRole;
import com.xowns.celfeed.dto.member.MemberIdNickname;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    Slice<Member> findByNicknameStartingWithAndRole(String nickname, MemberRole role, Pageable pageable);

    Optional<Member> findByEmail(String email);

    List<MemberIdNickname> findByIdIn(List<Long> id);

}

