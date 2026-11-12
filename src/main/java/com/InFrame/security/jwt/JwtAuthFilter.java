package com.InFrame.security.jwt;

import com.InFrame.security.userdetails.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 유효한 토큰이면 인증 정보를 설정하고, 아니면 익명으로 다음 필터로 넘긴다.
 * 인증이 필요한 API 인지는 여기서 판단하지 않는다 (SecurityConfig + EntryPoint 가 401 응답).
 * → 잘못된 토큰이 있어도 공개 API 는 정상 동작한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = jwtUtil.resolveToken(request);

        if (token != null) {
            jwtUtil.parseClaims(token)
                    .map(claims -> claims.get("email", String.class))
                    .ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String email) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);
        } catch (UsernameNotFoundException e) {
            // 탈퇴 등으로 토큰의 유저가 없는 경우 → 익명 처리
            log.debug("토큰의 유저를 찾을 수 없습니다.");
        }
    }
}
