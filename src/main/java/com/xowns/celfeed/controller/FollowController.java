package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.argumentresolver.Login;
import com.xowns.celfeed.controller.response.ApiResponse;
import com.xowns.celfeed.controller.response.ResponseEntityUtils;
import com.xowns.celfeed.dto.member.MemberResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetMemberId}")
    public ResponseEntity<ApiResponse> follow(@Login Long memberId, @PathVariable Long targetMemberId) {
        followService.followMember(memberId, targetMemberId);
        return ResponseEntityUtils.create("팔로우 성공");
    }

    @DeleteMapping("/{targetMemberId}")
    public ResponseEntity<ApiResponse> unfollow(@Login Long memberId, @PathVariable Long targetMemberId) {
        followService.unfollowMember(memberId, targetMemberId);
        return ResponseEntityUtils.ok("언팔로우 성공");
    }

    @GetMapping("/following")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowing(@Login Long memberId, Pageable pageable) {
        SliceDTO<MemberResponse> following = followService.getFollowingMembers(memberId, pageable);
        return ResponseEntityUtils.ok("팔로잉 목록 조회 성공", following);
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowers(@Login Long memberId, Pageable pageable) {
        SliceDTO<MemberResponse> followers = followService.getFollowerMembers(memberId, pageable);
        return ResponseEntityUtils.ok("팔로워 목록 조회 성공", followers);
    }
}
