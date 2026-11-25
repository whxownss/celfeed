package com.xowns.celfeed.server.social.controller;

import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import com.xowns.celfeed.server.social.dto.response.MemberResponse;
import com.xowns.celfeed.server.social.service.FollowService;
import com.xowns.celfeed.server.social.service.MemberService;
import com.xowns.celfeed.server.social.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.xowns.celfeed.server.social.support.Fixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberService memberService;

    @MockitoBean
    PostService postService;

    @MockitoBean
    FollowService followService;

    private static final String VALID_JOIN = """
            {"nickname": "철수", "email": "chulsoo@test.com", "password": "password1"}
            """;

    private static MockHttpSession loginSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, memberId);
        return session;
    }

    @Nested
    @DisplayName("회원가입")
    class Join {

        @Test
        @DisplayName("로그인 없이 가입할 수 있고, 201 과 회원 id 를 내려준다")
        void join() throws Exception {
            given(memberService.join(any())).willReturn(1L);

            mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(VALID_JOIN))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("성공적으로 가입되었습니다."))
                    .andExpect(jsonPath("$.data").value(1));
        }

        @Test
        @DisplayName("빈 닉네임은 Size 보다 NotBlank 메시지를 우선해서 내려준다")
        void blankNickname() throws Exception {
            mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nickname": "", "email": "chulsoo@test.com", "password": "password1"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorMessage").value("유효한 값을 입력해 주세요."))
                    .andExpect(jsonPath("$.errorData.length()").value(1))
                    .andExpect(jsonPath("$.errorData[0].field").value("nickname"))
                    .andExpect(jsonPath("$.errorData[0].message").value("닉네임은 필수값입니다."));
            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("여러 필드가 잘못되면 필드마다 하나씩 에러를 내려준다")
        void multipleInvalidFields() throws Exception {
            mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nickname": "a", "email": "not-email", "password": "short"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorData[*].field", containsInAnyOrder("nickname", "email", "password")))
                    .andExpect(jsonPath("$.errorData[*].message", containsInAnyOrder(
                            "닉네임은 2자 이상 15자 이내로 입력해 주세요.",
                            "이메일 형식이 올바르지 않습니다.",
                            "비밀번호는 7자 이상 20자 이내로 입력해 주세요.")));
        }

        @Test
        @DisplayName("중복 닉네임이면 409 로 응답한다")
        void duplicate() throws Exception {
            willThrow(new ApiException(ErrorCode.DUPLICATE_NICKNAME)).given(memberService).join(any());

            mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(VALID_JOIN))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorMessage").value("이미 등록된 닉네임입니다."));
        }

        @Test
        @DisplayName("닉네임/이메일 중복 확인은 로그인 없이 호출할 수 있다")
        void validation() throws Exception {
            mockMvc.perform(get("/api/members/validation/nickname").param("nickname", "철수"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("사용 가능한 닉네임입니다."));
            mockMvc.perform(get("/api/members/validation/email").param("email", "a@test.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("사용 가능한 이메일입니다."));
        }
    }

    @Nested
    @DisplayName("로그인/로그아웃")
    class LoginLogout {

        @Test
        @DisplayName("로그인에 성공하면 세션에 회원 id 를 저장한다")
        void login() throws Exception {
            given(memberService.login(any())).willReturn(7L);

            mockMvc.perform(post("/api/members/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "a@test.com", "password": "password1"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(request().sessionAttribute(SessionConst.LOGIN_MEMBER, 7L));
        }

        @Test
        @DisplayName("로그인에 실패하면 401 이고 세션을 만들지 않는다")
        void loginFail() throws Exception {
            willThrow(new ApiException(ErrorCode.LOGIN_FAIL)).given(memberService).login(any());

            MvcResult result = mockMvc.perform(post("/api/members/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email": "a@test.com", "password": "wrong"}
                            """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorMessage").value("로그인 정보가 올바르지 않습니다."))
                    .andReturn();

            assertThat(result.getRequest().getSession(false)).isNull();
        }

        @Test
        @DisplayName("로그인 요청에 이메일/비밀번호가 없으면 400")
        void loginValidation() throws Exception {
            mockMvc.perform(post("/api/members/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorData[*].field", containsInAnyOrder("email", "password")));
            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("로그아웃하면 세션을 무효화한다")
        void logout() throws Exception {
            MockHttpSession session = loginSession(7L);

            mockMvc.perform(delete("/api/members/logout").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("로그아웃에 성공했습니다."));

            assertThat(session.isInvalid()).isTrue();
        }
    }

    @Nested
    @DisplayName("회원 조회")
    class Find {

        @Test
        @DisplayName("회원 조회는 로그인이 필요하다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get("/api/members/1")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("회원 응답은 비밀번호를 포함하지 않고, 가입일은 yyyy-MM-dd 형식이다")
        void memberResponse() throws Exception {
            given(memberService.findOne(1L)).willReturn(MemberResponse.of(member(1L, "철수")));

            mockMvc.perform(get("/api/members/1").session(loginSession(7L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.nickname").value("철수"))
                    .andExpect(jsonPath("$.data.role").value("FAN"))
                    .andExpect(jsonPath("$.data.createdAt").value("2026-11-01"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }
    }
}
