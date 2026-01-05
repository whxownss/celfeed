package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.dto.MemberDTO;
import com.xowns.celfeed.dto.MemberResponse;
import com.xowns.celfeed.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public void validateDuplicateNickname(String nickname) {
        if (!memberRepository.existsByNickname(nickname)) {

        }
    }

    public void validateDuplicateEmail(String email) {
        if (!memberRepository.existsByEmail(email)) {

        }
    }

    public MemberResponse join(MemberDTO memberDTO) {

        validateDuplicateNickname(memberDTO.getNickname());
        validateDuplicateEmail(memberDTO.getEmail());

        Member savedMember = memberRepository.save(memberDTO.toEntity());
        return MemberResponse.from(savedMember);
    }


}
