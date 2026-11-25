package com.xowns.celfeed.common.web.argumentresolver;

import com.xowns.celfeed.common.web.consts.SessionConst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LoginMemberArgumentResolverTest {

    private final LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();

    @SuppressWarnings("unused")
    private void handler(@Login Long loginId, @Login String loginName, Long memberId) {
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = LoginMemberArgumentResolverTest.class
                .getDeclaredMethod("handler", Long.class, String.class, Long.class);
        return new MethodParameter(method, index);
    }

    @Test
    @DisplayName("@Login 이 붙은 Long 파라미터만 지원한다")
    void supportsParameter() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();  // @Login Long
        assertThat(resolver.supportsParameter(parameter(1))).isFalse(); // @Login String
        assertThat(resolver.supportsParameter(parameter(2))).isFalse(); // Long (어노테이션 없음)
    }

    @Test
    @DisplayName("세션의 로그인 회원 id 를 반환한다")
    void resolveLoginId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER, 7L);
        request.setSession(session);

        Object result = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("세션이 없으면 null 을 반환하고, 세션을 새로 만들지 않는다")
    void noSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object result = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        assertThat(result).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("세션은 있지만 로그인 정보가 없으면 null 을 반환한다")
    void sessionWithoutLoginMember() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        Object result = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        assertThat(result).isNull();
    }
}
