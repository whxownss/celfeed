package com.xowns.celfeed.common.web.config;

import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.support.TestController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 WebConfig(인터셉터 등록/제외 경로, @Login 리졸버)와 GlobalExceptionHandler 를 함께 띄워 검증
 */
@WebMvcTest(TestController.class)
class WebConfigMvcTest {

    @Autowired
    MockMvc mockMvc;

    private static MockHttpSession loginSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, memberId);
        return session;
    }

    @Nested
    @DisplayName("로그인 체크 인터셉터")
    class LoginCheck {

        @Test
        @DisplayName("로그인하지 않으면 401 과 에러 응답을 내려준다")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/test/login-id"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMessage").value("로그인이 필요합니다."))
                    .andExpect(jsonPath("$.errorData").doesNotExist());
        }

        @Test
        @DisplayName("SSE 요청은 로그인하지 않으면 본문 없이 401 만 내려준다")
        void sseUnauthorized() throws Exception {
            mockMvc.perform(get("/test/login-id").accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("제외 경로(로그인, 닉네임 검증)는 로그인 없이 접근할 수 있다")
        void excludedPaths() throws Exception {
            mockMvc.perform(post("/api/members/login"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/members/validation/nickname"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("회원가입(POST /api/members)은 통과하지만 회원 목록(GET /api/members)은 로그인이 필요하다")
        void joinOnlyPostPasses() throws Exception {
            mockMvc.perform(post("/api/members"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/members"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("@Login 리졸버")
    class LoginResolver {

        @Test
        @DisplayName("세션의 로그인 회원 id 가 컨트롤러 파라미터로 들어오고, 성공 응답 형식으로 내려간다")
        void resolveLoginId() throws Exception {
            mockMvc.perform(get("/test/login-id").session(loginSession(42L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("로그인 아이디"))
                    .andExpect(jsonPath("$.data").value(42));
        }

        @Test
        @DisplayName("data 가 없으면 응답에서 data 필드를 생략한다")
        void omitNullData() throws Exception {
            mockMvc.perform(post("/api/members/login"))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GlobalExceptionHandler")
    class ExceptionHandling {

        @Test
        @DisplayName("ApiException 은 ErrorCode 의 상태 코드와 메시지로 응답한다")
        void apiException() throws Exception {
            mockMvc.perform(get("/test/api-exception").session(loginSession(1L)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMessage").value("존재하지 않는 회원입니다."));
        }

        @Test
        @DisplayName("경로 변수 타입이 맞지 않으면 400 과 잘못된 값을 내려준다")
        void typeMismatch() throws Exception {
            mockMvc.perform(get("/test/type-mismatch/abc").session(loginSession(1L)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorMessage").value("요청 값의 형식이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.errorData.id").value("abc"));
        }

        @Test
        @DisplayName("요청 본문을 읽을 수 없으면 400 과 경로/메서드를 내려준다")
        void notReadable() throws Exception {
            mockMvc.perform(post("/test/body").session(loginSession(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorMessage").value("요청 본문의 형식이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.errorData.path").value("/test/body"))
                    .andExpect(jsonPath("$.errorData.method").value("POST"));
        }

        @Test
        @DisplayName("DataIntegrityViolationException 은 409 로 응답한다")
        void dataIntegrityViolation() throws Exception {
            mockMvc.perform(get("/test/data-integrity").session(loginSession(1L)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorMessage").value("이미 존재하는 리소스입니다."));
        }

        @Test
        @DisplayName("그 외 DataAccessException 은 500 으로 응답한다")
        void dataAccess() throws Exception {
            mockMvc.perform(get("/test/data-access").session(loginSession(1L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorMessage").value("서버 내부에 오류가 발생하였습니다."));
        }

        @Test
        @DisplayName("처리되지 않은 예외는 내부 메시지를 노출하지 않고 500 으로 응답한다")
        void unknown() throws Exception {
            mockMvc.perform(get("/test/unknown").session(loginSession(1L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorMessage").value("알 수 없는 오류가 발생하였습니다."))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("unexpected"))));
        }
    }

    /**
     * 현재 동작을 기록하는 테스트 (코드 주석의 질문에 대한 답)
     * 동작을 바꾸기로 하면 이 테스트도 같이 수정한다.
     */
    @Nested
    @DisplayName("현재 동작 기록")
    class CurrentBehavior {

        @Test
        @DisplayName("[왜 안잡히지?] 지원하지 않는 METHOD 는 핸들러가 정해지기 전에 실패해서 "
                + "@RestControllerAdvice(annotations = RestController.class) 가 적용되지 않는다 → 기본 405")
        void methodNotSupportedIsNotHandledByAdvice() throws Exception {
            mockMvc.perform(delete("/test/login-id").session(loginSession(1L)))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("지원하지 않는 METHOD"))));
        }

        @Test
        @DisplayName("[MissingServletRequestParameterException 이것도?] 필수 파라미터 누락은 별도 핸들러가 없어 500 으로 응답한다")
        void missingRequestParamFallsBackTo500() throws Exception {
            mockMvc.perform(get("/test/required-param").session(loginSession(1L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorMessage").value("알 수 없는 오류가 발생하였습니다."));
        }
    }
}
