package com.InFrame.security.oauth2;

import com.InFrame.security.jwt.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;

    @Value("${app.oauth2.redirect-url}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken = jwtUtil.createAccessToken(
                oAuth2User.getUser().getId(),
                oAuth2User.getUser().getEmail(),
                oAuth2User.getUser().getRole().name()
        );


        // fragment(#)는 브라우저가 서버로 보내지 않아 액세스 로그, Referer, 프록시에 토큰이 남지 않음
        String finalRedirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("token=" + accessToken)
                .build().toUriString();

        response.sendRedirect(finalRedirectUrl);
    }
}
