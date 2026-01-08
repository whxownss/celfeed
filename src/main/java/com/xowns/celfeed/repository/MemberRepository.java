package com.xowns.celfeed.repository;

import com.xowns.celfeed.domain.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    Slice<Member> findByNicknameStartingWith(String nickname, Pageable pageable);
}
