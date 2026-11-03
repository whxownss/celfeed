package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.server.social.dto.request.MemberLoginRequest;
import com.xowns.celfeed.server.social.dto.request.MemberRequest;
import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import com.xowns.celfeed.server.social.service.MemberService;
import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.dto.ApiResponse;
import com.xowns.celfeed.common.web.dto.PageDTO;
import com.xowns.celfeed.common.web.dto.SliceDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/validation/nickname")
    @ResponseStatus(OK)
    public ApiResponse<Void> validateNickname(@RequestParam String nickname) {
        memberService.validateDuplicateNickname(nickname);
        return ApiResponse.of("사용 가능한 닉네임입니다.");
    }

    @GetMapping("/validation/email")
    @ResponseStatus(OK)
    public ApiResponse<Void> validateEmail(@RequestParam String email) {
        memberService.validateDuplicateNickname(email);
        return ApiResponse.of("사용 가능한 이메일입니다.");
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public ApiResponse<Long> createMember(@Valid @RequestBody MemberRequest memberRequest) {
        Long memberId = memberService.join(memberRequest);
        return ApiResponse.of("성공적으로 가입되었습니다.", memberId);
    }

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    public ApiResponse<MemberResponse> getMember(@PathVariable Long id) {
        MemberResponse findMember = memberService.findOne(id);
        return ApiResponse.of("회원 조회 성공", findMember);
    }

    @GetMapping
    @ResponseStatus(OK)
    public ApiResponse<PageDTO<MemberResponse>> getMembers(Pageable pageable) {
        PageDTO<MemberResponse> findMembers = memberService.findAll(pageable);
        return ApiResponse.of("회원 목록 조회 성공", findMembers);
    }

    @GetMapping("/search")
    @ResponseStatus(OK)
    public ApiResponse<SliceDTO<MemberResponse>> searchMembersByNickname(@RequestParam String nickname,
                                                                         Pageable pageable) {

        SliceDTO<MemberResponse> findMembers = memberService.findAllByNickname(nickname, pageable);
        return ApiResponse.of("회원 목록 검색 성공", findMembers);
    }

    @PostMapping("/login")
    @ResponseStatus(OK)
    public ApiResponse<Void> login(@Valid @RequestBody MemberLoginRequest memberLoginRequest,
                                   HttpServletRequest request) {

        Long memberId = memberService.login(memberLoginRequest);

        HttpSession session = request.getSession(true);
        session.setAttribute(SessionConst.LOGIN_MEMBER, memberId);

        return ApiResponse.of("로그인 성공");
    }

    @DeleteMapping("/logout")
    @ResponseStatus(OK)
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ApiResponse.of("로그아웃 성공");
    }
}