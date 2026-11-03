package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import com.xowns.celfeed.server.social.service.FollowService;
import com.xowns.celfeed.common.web.argumentresolver.Login;
import com.xowns.celfeed.common.web.dto.ApiResponse;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetMemberId}")
    @ResponseStatus(CREATED)
    public ApiResponse<Void> follow(@Login Long loginId, @PathVariable Long targetMemberId) {
        followService.followMember(loginId, targetMemberId);
        return ApiResponse.of("팔로우를 걸었습니다.");
    }

    @DeleteMapping("/{targetMemberId}")
    @ResponseStatus(OK)
    public ApiResponse<Void> unfollow(@Login Long loginId, @PathVariable Long targetMemberId) {
        followService.unfollowMember(loginId, targetMemberId);
        return ApiResponse.of("팔로우를 취소했습니다.");
    }

    @GetMapping("/following")
    @ResponseStatus(OK)
    public ApiResponse<SliceDTO<MemberResponse>> getFollowing(@Login Long loginId, Pageable pageable) {
        SliceDTO<MemberResponse> following = followService.getFollowingMembers(loginId, pageable);
        return ApiResponse.of("팔로잉 목록이 조회되었습니다.", following);
    }

    @GetMapping("/followers")
    @ResponseStatus(OK)
    public ApiResponse<SliceDTO<MemberResponse>> getFollowers(@Login Long loginId, Pageable pageable) {
        SliceDTO<MemberResponse> followers = followService.getFollowerMembers(loginId, pageable);
        return ApiResponse.of("팔로워 목록이 조회되었습니다.", followers);
    }
}
