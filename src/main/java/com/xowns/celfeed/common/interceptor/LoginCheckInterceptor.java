package com.xowns.celfeed.common.interceptor;

import com.xowns.celfeed.common.consts.SessionConst;
import com.xowns.celfeed.exception.ApiException;
import com.xowns.celfeed.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("==== loginCheck ====");

        if (request.getRequestURI().equals("/api/members") && HttpMethod.POST.matches(request.getMethod()))
            return true;

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionConst.LOGIN_MEMBER) == null) {
            if (request.getHeader(HttpHeaders.ACCEPT).equals(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }

            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return true;
    }
}
