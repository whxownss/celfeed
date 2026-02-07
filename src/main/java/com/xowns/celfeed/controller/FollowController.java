package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.argumentresolver.Login;
import com.xowns.celfeed.controller.response.ApiResponse;
import com.xowns.celfeed.controller.response.ResponseEntityUtils;
import com.xowns.celfeed.dto.member.MemberResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.service.basic.FollowService;
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
    public ResponseEntity<ApiResponse<Void>> follow(@Login Long loginId, @PathVariable Long targetMemberId) {
        followService.followMember(loginId, targetMemberId);
        return ResponseEntityUtils.create("팔로우 성공");
    }

    @DeleteMapping("/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> unfollow(@Login Long loginId, @PathVariable Long targetMemberId) {
        followService.unfollowMember(loginId, targetMemberId);
        return ResponseEntityUtils.ok("언팔로우 성공");
    }

    @GetMapping("/following")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowing(@Login Long loginId, Pageable pageable) {
        SliceDTO<MemberResponse> following = followService.getFollowingMembers(loginId, pageable);
        return ResponseEntityUtils.ok("팔로잉 목록 조회 성공", following);
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowers(@Login Long loginId, Pageable pageable) {
        SliceDTO<MemberResponse> followers = followService.getFollowerMembers(loginId, pageable);
        return ResponseEntityUtils.ok("팔로워 목록 조회 성공", followers);
    }
}
