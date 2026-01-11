package com.xowns.celfeed.controller;

import com.xowns.celfeed.common.SessionConst;
import com.xowns.celfeed.controller.response.ApiResponse;
import com.xowns.celfeed.controller.response.ResponseEntityUtils;
import com.xowns.celfeed.dto.*;
import com.xowns.celfeed.dto.member.MemberLoginRequest;
import com.xowns.celfeed.dto.member.MemberRequest;
import com.xowns.celfeed.dto.member.MemberResponse;
import com.xowns.celfeed.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/validation/nickname")
    public ResponseEntity<ApiResponse> validateNickname(@RequestParam String nickname) {
        memberService.validateDuplicateNickname(nickname);
        return ResponseEntityUtils.ok("사용 가능한 닉네임입니다.");
    }

    @GetMapping("/validation/email")
    public ResponseEntity<ApiResponse> validateEmail(@RequestParam String email) {
        memberService.validateDuplicateNickname(email);
        return ResponseEntityUtils.ok("사용 가능한 이메일입니다.");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMember(@Valid @RequestBody MemberRequest memberRequest) {
        return ResponseEntityUtils.create("성공적으로 가입되었습니다.", memberService.join(memberRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(@PathVariable Long id) {
        return ResponseEntityUtils.ok("회원 조회 성공", memberService.findOne(id));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageDTO<MemberResponse>>> getMembers(Pageable pageable) {
        return ResponseEntityUtils.ok("회원 목록 조회 성공", memberService.findAll(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SliceDTO<MemberResponse>>> searchMembersByNickname(@RequestParam String nickname, Pageable pageable) {
        return ResponseEntityUtils.ok("회원 목록 검색 성공", memberService.findAllByNickname(nickname, pageable));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody MemberLoginRequest memberLoginRequest, HttpSession session) {
        Long memberId = memberService.login(memberLoginRequest);
        session.setAttribute(SessionConst.LOGIN_MEMBER, memberId);

        return ResponseEntityUtils.ok("로그인 성공");
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntityUtils.ok("로그아웃 성공");
    }
}

































