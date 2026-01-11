package com.xowns.celfeed.service;

import com.xowns.celfeed.domain.Follow;
import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.dto.member.MemberResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.FollowRepository;
import com.xowns.celfeed.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long followMember(Long memberId, Long targetMemberId) {

        if (memberId.equals(targetMemberId)) {
            throw new ApiException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }

        Member fromMember = findMember(memberId);
        Member toMember = findMember(targetMemberId);

        Optional<Follow> optionalFollow = followRepository.findByFromMemberAndToMember(fromMember, toMember);
        if (optionalFollow.isPresent()) {
            return optionalFollow.get().getId();
        }

        Follow savedFollow = followRepository.save(Follow.create(fromMember, toMember));
        return savedFollow.getId();
    }

    @Transactional
    public void unfollowMember(Long memberId, Long targetMemberId) {
        Member fromMember = findMember(memberId);
        Member toMember = findMember(targetMemberId);

        followRepository.findByFromMemberAndToMember(fromMember, toMember)
                .ifPresent(followRepository::delete);
    }

    public SliceDTO<MemberResponse> getFollowingMembers(Long memberId, Pageable pageable) {
        Member fromMember = findMember(memberId);
        Slice<MemberResponse> followingSlice = followRepository.findByFromMember(fromMember, pageable)
                .map(follow -> MemberResponse.of(follow.getToMember()));

        return SliceDTO.of(followingSlice);
    }

    public SliceDTO<MemberResponse> getFollowerMembers(Long memberId, Pageable pageable) {
        Member toMember = findMember(memberId);
        Slice<MemberResponse> followerSlice = followRepository.findByToMember(toMember, pageable)
                .map(follow -> MemberResponse.of(follow.getFromMember()));

        return SliceDTO.of(followerSlice);
    }

    // ====
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
