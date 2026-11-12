package com.InFrame.security;

import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import com.InFrame.security.jwt.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthSecurityTest {

    // 로컬 테스트용 키와 다른 키로 서명한 토큰 (서명 위조)
    private static final String FORGED_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJhQGEuY29tIiwiZXhwIjo0MTAyNDQ0ODAwfQ."
                    + "Zm9yZ2VkLXNpZ25hdHVyZS1mb3JnZWQtc2lnbmF0dXJlLWZvcmdlZA";

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserRepository userRepository;

    private User savedUser;

    @AfterEach
    void cleanUp() {
        if (savedUser != null && userRepository.existsById(savedUser.getId())) {
            userRepository.deleteById(savedUser.getId());
        }
    }

    @Test
    @DisplayName("토큰 없이 인증 필요 API 호출 → 401")
    void noToken_protectedApi_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("토큰 없이 공개 API 호출 → 200")
    void noToken_publicApi_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/host/map"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
            "/api/v1/host/me",
            "/api/v1/host/reservations",
            "/api/v1/host/check-business-number?businessNumber=1234567890",
            "/api/v1/host/experiences/my-list",
            "/api/v1/likes/experience",
            "/api/v1/likes/host",
            "/api/v1/reservation/me",
    })
    @DisplayName("공개 패턴과 겹치는 인증 필요 GET → 토큰 없으면 401")
    void protectedGet_withoutToken_returns401(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
            "/api/v1/enums/categories",
            "/api/v1/host/map",
            "/api/v1/host/top5",
            "/swagger-ui/index.html",
            "/v3/api-docs",
    })
    @DisplayName("공개 GET → 토큰 없어도 401/403 아님")
    void publicGet_withoutToken_isNotBlocked(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 401 || s == 403) throw new AssertionError(url + " blocked: " + s);
        });
    }

    @Test
    @DisplayName("토큰 없이 쓰기 API 호출 → 401")
    void noToken_writeApi_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reservation").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("서명이 위조된 토큰으로 공개 API 호출 → 익명으로 처리되어 200")
    void forgedToken_publicApi_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/host/map").header("Authorization", "Bearer " + FORGED_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명이 위조된 토큰으로 인증 필요 API 호출 → 401")
    void forgedToken_protectedApi_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + FORGED_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴한 유저의 토큰으로 인증 필요 API 호출 → 401")
    void deletedUserToken_protectedApi_returns401() throws Exception {
        String token = jwtUtil.createAccessToken(999_999L, "deleted-" + UUID.randomUUID() + "@test.com", "USER");

        mockMvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 토큰으로 인증 필요 API 호출 → 200")
    void validToken_protectedApi_returns200() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        savedUser = userRepository.save(User.builder()
                .email("auth-" + suffix + "@test.com")
                .password("encoded")
                .nickname("n" + suffix)
                .name("테스터")
                .role(Role.USER)
                .build());
        String token = jwtUtil.createAccessToken(savedUser.getId(), savedUser.getEmail(), "USER");

        mockMvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
