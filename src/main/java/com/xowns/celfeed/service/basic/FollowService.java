package com.xowns.celfeed.service.basic;

import com.xowns.celfeed.domain.basic.Follow;
import com.xowns.celfeed.domain.basic.Member;
import com.xowns.celfeed.dto.member.MemberResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import com.xowns.celfeed.repository.basic.FollowRepository;
import com.xowns.celfeed.repository.basic.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long followMember(Long loginId, Long targetMemberId) {
        if (loginId.equals(targetMemberId)) {
            throw new ApiException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }

        Member fromMember = getMemberOrThrow(loginId);
        Member toMember = getMemberOrThrow(targetMemberId);

        return followRepository.findByFromMemberAndToMember(fromMember, toMember)
                .map(Follow::getId)
                .orElseGet(() -> {
                    Follow follow = Follow.create(fromMember, toMember);
                    return followRepository.save(follow).getId();
                });
    }

    @Transactional
    public void unfollowMember(Long loginId, Long targetMemberId) {
        Member fromMember = getMemberOrThrow(loginId);
        Member toMember = getMemberOrThrow(targetMemberId);

        followRepository.findByFromMemberAndToMember(fromMember, toMember)
                .ifPresent(followRepository::delete);
    }

    public SliceDTO<MemberResponse> getFollowingMembers(Long loginId, Pageable pageable) {
        Member fromMember = getMemberOrThrow(loginId);
        Slice<MemberResponse> followingSlice = followRepository.findByFromMember(fromMember, pageable)
                .map(follow -> MemberResponse.of(follow.getToMember()));

        return SliceDTO.of(followingSlice);
    }

    public SliceDTO<MemberResponse> getFollowerMembers(Long loginId, Pageable pageable) {
        Member toMember = getMemberOrThrow(loginId);
        Slice<MemberResponse> followerSlice = followRepository.findByToMember(toMember, pageable)
                .map(follow -> MemberResponse.of(follow.getFromMember()));

        return SliceDTO.of(followerSlice);
    }

    // ====
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
