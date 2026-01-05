package com.xowns.celfeed.controller;

import com.xowns.celfeed.dto.MemberDTO;
import com.xowns.celfeed.dto.MemberResponse;
import com.xowns.celfeed.service.MemberService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/validate/nickname")
    public ResponseEntity<ApiResponse<String>> validateNickname(@RequestParam String nickname) {
        memberService.validateDuplicateNickname(nickname);
        return ResponseEntity.ok(ApiResponse.of("사용 가능한 닉네임입니다.", nickname));
    }

    @GetMapping("/validate/email")
    public ResponseEntity<ApiResponse<String>> validateEmail(@RequestParam String email) {
        memberService.validateDuplicateNickname(email);
        return ResponseEntity.ok(ApiResponse.of("사용 가능한 이메일입니다.", email));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemberResponse>> createMember(@RequestBody MemberDTO memberDTO) {
        ApiResponse<MemberResponse> apiResponse =
                ApiResponse.of("성공적으로 가입되었습니다.", memberService.join(memberDTO));
        return ResponseEntity.status(CREATED).body(apiResponse);
    }
}
