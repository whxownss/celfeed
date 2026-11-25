package com.xowns.celfeed.common.web.interceptor;

import com.xowns.celfeed.common.web.consts.SessionConst;
import com.xowns.celfeed.common.web.exception.ApiException;
import com.xowns.celfeed.common.web.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginCheckInterceptorTest {

    private final LoginCheckInterceptor interceptor = new LoginCheckInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("회원가입(POST /api/members)은 로그인 없이 통과한다")
    void joinPassesWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/members");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("같은 경로라도 POST 가 아니면 로그인이 필요하다")
    void getMembersRequiresLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members");

        assertUnauthorized(request);
    }

    @Test
    @DisplayName("세션이 없으면 UNAUTHORIZED 예외를 던진다")
    void noSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");

        assertUnauthorized(request);
    }

    @Test
    @DisplayName("세션은 있지만 로그인 정보가 없으면 UNAUTHORIZED 예외를 던진다")
    void sessionWithoutLoginMember() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setSession(new MockHttpSession());

        assertUnauthorized(request);
    }

    @Test
    @DisplayName("SSE 요청은 예외 대신 401 상태만 내려주고 요청을 중단한다")
    void sseRequestWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/subscribe");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("로그인 정보가 있으면 통과한다")
    void loggedIn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, 1L);
        request.setSession(session);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private void assertUnauthorized(MockHttpServletRequest request) {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
