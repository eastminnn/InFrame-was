package com.InFrame.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtil {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final long ACCESS_TOKEN = 60 * 60 * 1000L; // 1시간
    private final long REFRESH_TOKEN = 7 * 24 * 60 * 60 * 1000L; // 7일

    @Value("${jwt.secret.key}")
    private String secretKey;

    private Key key;

    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    @PostConstruct
    public void init() {  // Key 재사용을 위한 코드
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    // Access 토큰 생성
    public String createAccessToken(Long id, String email, String role) {
        return createToken(id.toString(), email, role, ACCESS_TOKEN);
    }

    // Refresh 토큰 생성
    public String createRefreshToken(String email) {
        return createToken(null, email, null, REFRESH_TOKEN);
    }
    // JWT 생성
    private String createToken(String subject, String email, String role, long validTime) {
        Date now = new Date();

        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now) // 토큰 발급 시간
                .setExpiration(new Date(now.getTime() + validTime)) // 토큰 만료 시간
                .signWith(key, signatureAlgorithm);

        if (subject != null) {
            builder.setSubject(subject);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        if (role != null) {
            builder.claim("role", role);
        }

        return builder.compact();
    }

    public String resolveToken(HttpServletRequest request) { // 토큰 값 추출
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 토큰을 검증하고 클레임을 반환한다. 검증에 실패하면 empty.
     * jjwt의 모든 검증 예외는 JwtException 하위이므로 한 번에 처리한다.
     * (기존 코드는 java.lang.SecurityException 을 잡고 있어 SignatureException 이 누락됐음)
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody());
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰입니다: {}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }
}
