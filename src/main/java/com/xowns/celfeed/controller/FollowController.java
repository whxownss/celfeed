package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.argumentresolver.Login;
import com.xowns.celfeed.domain.Follow;
import com.xowns.celfeed.dto.MemberResponse;
import com.xowns.celfeed.dto.SliceDTO;
import com.xowns.celfeed.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetMemberId}")
    public ResponseEntity<ApiResponse> follow(@Login Long memberId, @PathVariable Long targetMemberId) {
        followService.followMember(memberId, targetMemberId);
        return ResponseEntity.status(CREATED).body(ApiResponse.of("팔로우 성공"));
    }

    @DeleteMapping("/{targetMemberId}")
    public ResponseEntity<ApiResponse> unfollow(@Login Long memberId, @PathVariable Long targetMemberId) {
        followService.unfollowMember(memberId, targetMemberId);
        return ResponseEntity.ok(ApiResponse.of("언팔로우 성공"));
    }

    @GetMapping("/following")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowing(@Login Long memberId, Pageable pageable) {
        SliceDTO<MemberResponse> following = followService.getFollowingMembers(memberId, pageable);
        return ResponseEntity.ok(ApiResponse.of("팔로잉 목록 조회 성공", following));
    }

    @GetMapping("/followers")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> getFollowers(@Login Long memberId, Pageable pageable) {
        SliceDTO<MemberResponse> followers = followService.getFollowerMembers(memberId, pageable);
        return ResponseEntity.ok(ApiResponse.of("팔로워 목록 조회 성공", followers));
    }
}
