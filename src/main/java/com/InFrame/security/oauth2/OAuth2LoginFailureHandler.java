package com.InFrame.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * 소셜 로그인 실패 시 프론트 리다이렉트 URL 로 오류 코드를 전달한다.
 * (기본 동작은 formLogin 이 꺼져 있어 존재하지 않는 /login?error 로 이동)
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-url}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = exception instanceof OAuth2AuthenticationException e
                ? e.getError().getErrorCode()
                : "login_failed";

        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("error=" + code)
                .build().toUriString());
    }
}
