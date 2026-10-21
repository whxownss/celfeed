package com.xowns.celfeed.app.service;

import com.xowns.celfeed.app.dto.PageDTO;
import com.xowns.celfeed.app.dto.SliceDTO;
import com.xowns.celfeed.app.dto.request.MemberLoginRequest;
import com.xowns.celfeed.app.dto.request.MemberRequest;
import com.xowns.celfeed.app.dto.response.MemberResponse;
import com.xowns.celfeed.app.exception.ApiException;
import com.xowns.celfeed.app.exception.ErrorCode;
import com.xowns.celfeed.domain.entity.basic.Member;
import com.xowns.celfeed.domain.entity.basic.MemberRole;
import com.xowns.celfeed.domain.repository.basic.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public void validateDuplicateNickname(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new ApiException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    public void validateDuplicateEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional
    public Long join(MemberRequest memberRequest) {
        validateDuplicateNickname(memberRequest.getNickname());
        validateDuplicateEmail(memberRequest.getEmail());

        Member savedMember = memberRepository.save(memberRequest.toEntity());
        return savedMember.getId();
    }

    public MemberResponse findOne(Long memberId) {
        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponse.of(findMember);
    }

    public PageDTO<MemberResponse> findAll(Pageable pageable) {
        Page<MemberResponse> members = memberRepository.findAll(pageable).map(MemberResponse::of);
        return PageDTO.of(members);
    }

    public SliceDTO<MemberResponse> findAllByNickname(String nickname, Pageable pageable) {
        pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                    Sort.by("nickname").ascending());

        Slice<MemberResponse> members =
                memberRepository.findByNicknameStartingWithAndRole(nickname, MemberRole.CELEB, pageable)
                                .map(MemberResponse::of);

        return SliceDTO.of(members);
    }

    public Long login(MemberLoginRequest memberLoginRequest) {
        Member loginMember = memberRepository.findByEmail(memberLoginRequest.getEmail())
                .filter(member -> memberLoginRequest.getPassword().equals(member.getPassword()))
                .orElseThrow(() -> new ApiException(ErrorCode.LOGIN_FAIL));

        return loginMember.getId();
    }
}
