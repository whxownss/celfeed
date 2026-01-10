package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.domain.MemberRole;
import com.xowns.celfeed.dto.*;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.MemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

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
    public Long join(MemberCreateRequest memberDTO) {
        validateDuplicateNickname(memberDTO.getNickname());
        validateDuplicateEmail(memberDTO.getEmail());

        Member savedMember = memberRepository.save(memberDTO.toEntity());
        return savedMember.getId();
    }

    public MemberResponse findOne(Long memberId) {
        Member findMember = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponse.of(findMember);
    }

    public PageDTO<MemberResponse> findAll(Pageable pageable) {
        Page<MemberResponse> page = memberRepository.findAll(pageable).map(MemberResponse::of);
        return PageDTO.of(page);
    }

    public SliceDTO<MemberResponse> findAllByNickname(String nickname, Pageable pageable) {
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                                    Sort.by("nickname").ascending());
        Slice<MemberResponse> slice = memberRepository.findByNicknameStartingWithAndRole(nickname, MemberRole.CELEB, pageRequest)
                                                    .map(MemberResponse::of);
        return SliceDTO.of(slice);
    }

    public Long login(MemberLoginRequest memberLoginRequest) {
        Member loginMember = memberRepository.findByEmail(memberLoginRequest.getEmail())
                .filter(member -> memberLoginRequest.getPassword().equals(member.getPassword()))
                .orElseThrow(() -> new ApiException(ErrorCode.LOGIN_FAIL));

        return loginMember.getId();
    }
}
