package com.xowns.celfeed.controller;

import com.xowns.celfeed.domain.Member;
import com.xowns.celfeed.dto.MemberDTO;
import com.xowns.celfeed.dto.MemberResponse;
import com.xowns.celfeed.dto.PageDTO;
import com.xowns.celfeed.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/validation/nickname")
    public ResponseEntity<ApiResponse<String>> validateNickname(@RequestParam String nickname) {
        memberService.validateDuplicateNickname(nickname);
        return ResponseEntity.ok(ApiResponse.of("사용 가능한 닉네임입니다.", nickname));
    }

    @GetMapping("/validation/email")
    public ResponseEntity<ApiResponse<String>> validateEmail(@RequestParam String email) {
        memberService.validateDuplicateNickname(email);
        return ResponseEntity.ok(ApiResponse.of("사용 가능한 이메일입니다.", email));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createMember(@Valid @RequestBody MemberDTO memberDTO) {
        ApiResponse<Long> apiResponse = ApiResponse.of("성공적으로 가입되었습니다.", memberService.join(memberDTO));
        return ResponseEntity.status(CREATED).body(apiResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("회원 조회 성공", memberService.findOne(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageDTO<MemberResponse>>> getMembers(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of("회원 목록 조회 성공", memberService.findAll(pageable)));
    }


}

































