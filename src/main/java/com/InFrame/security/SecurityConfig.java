package com.InFrame.security;

import com.InFrame.security.handler.SecurityErrorResponder;
import com.InFrame.security.jwt.JwtAuthFilter;
import com.InFrame.security.jwt.JwtUtil;
import com.InFrame.security.oauth2.CustomOAuth2UserService;
import com.InFrame.security.oauth2.OAuth2LoginFailureHandler;
import com.InFrame.security.oauth2.OAuth2LoginSuccessHandler;
import com.InFrame.security.userdetails.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final SecurityErrorResponder securityErrorResponder;

    private static final String[] SWAGGER_URL = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/swagger-ui.html"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        // 화이트리스트 방식: 명시한 공개 API 외에는 모두 인증 필요 (기본값 거부)
        // 순서 중요: 같은 경로 패턴에 걸리는 인증 필요 GET 을 공개 GET 보다 먼저 선언
        http.authorizeHttpRequests(auth -> auth
                // 시스템
                .requestMatchers(SWAGGER_URL).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                // 인증/회원가입
                .requestMatchers("/api/v1/auth/**").permitAll()

                // GET 중 로그인 필요 (아래 공개 패턴 /host/{hostId} 등과 겹치므로 먼저 선언)
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/host/me",
                        "/api/v1/host/reservations",
                        "/api/v1/host/check-business-number", // 외부 공공 API 호출 → 남용 방지
                        "/api/v1/host/experiences/my-list"
                ).authenticated()

                // 공개 조회 API
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/enums/**",
                        "/api/v1/experiences/*/available-slots",
                        "/api/v1/host/map",
                        "/api/v1/host/top5",
                        "/api/v1/host/*",
                        "/api/v1/host/experiences/recommend",
                        "/api/v1/host/experiences/*",
                        "/api/v1/host/experiences/host/**",
                        "/api/v1/reviews/**"
                ).permitAll()

                .anyRequest().authenticated()
        );

        http.exceptionHandling(e -> e
                .authenticationEntryPoint(securityErrorResponder.authenticationEntryPoint())
                .accessDeniedHandler(securityErrorResponder.accessDeniedHandler())
        );

        http.oauth2Login(oauth -> oauth
                .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler(oAuth2LoginFailureHandler)
        );

        http.addFilterBefore(new JwtAuthFilter(jwtUtil, userDetailsServiceImpl), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
