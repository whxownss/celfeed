package com.xowns.celfeed.common.web.support;

import com.xowns.celfeed.common.web.argumentresolver.Login;
import com.xowns.celfeed.common.web.dto.ApiResponse;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.web.bind.annotation.*;

/**
 * WebConfig(인터셉터, 리졸버)와 GlobalExceptionHandler 동작 확인용 테스트 컨트롤러
 * 경로는 WebConfig 의 인터셉터 제외 경로와 실제 API 경로에 맞춘다.
 */
@RestController
public class TestController {

    // ============================== 로그인 체크 제외 경로 ==============================

    @PostMapping("/api/members")
    public ApiResponse<Void> join() {
        return ApiResponse.of("회원가입");
    }

    @GetMapping("/api/members")
    public ApiResponse<Void> members() {
        return ApiResponse.of("회원 목록");
    }

    @PostMapping("/api/members/login")
    public ApiResponse<Void> login() {
        return ApiResponse.of("로그인");
    }

    @GetMapping("/api/members/validation/nickname")
    public ApiResponse<Void> validateNickname() {
        return ApiResponse.of("닉네임 검증");
    }

    // ============================== @Login ==============================

    @GetMapping("/test/login-id")
    public ApiResponse<Long> loginId(@Login Long loginId) {
        return ApiResponse.of("로그인 아이디", loginId);
    }

    // ============================== 예외 ==============================

    @GetMapping("/test/api-exception")
    public ApiResponse<Void> apiException() {
        throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
    }

    @GetMapping("/test/type-mismatch/{id}")
    // common-web 은 Boot 플러그인이 없어 -parameters 컴파일 옵션이 없으므로 이름을 명시
    public ApiResponse<Long> typeMismatch(@PathVariable("id") Long id) {
        return ApiResponse.of("ok", id);
    }

    @GetMapping("/test/required-param")
    public ApiResponse<String> requiredParam(@RequestParam("keyword") String keyword) {
        return ApiResponse.of("ok", keyword);
    }

    @PostMapping("/test/body")
    public ApiResponse<Body> body(@RequestBody Body body) {
        return ApiResponse.of("ok", body);
    }

    @GetMapping("/test/data-integrity")
    public ApiResponse<Void> dataIntegrity() {
        throw new DataIntegrityViolationException("duplicate key");
    }

    @GetMapping("/test/data-access")
    public ApiResponse<Void> dataAccess() {
        throw new DataRetrievalFailureException("connection lost");
    }

    @GetMapping("/test/unknown")
    public ApiResponse<Void> unknown() {
        throw new IllegalStateException("unexpected");
    }

    public record Body(String name, int age) {
    }
}
