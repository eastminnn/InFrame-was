package com.InFrame.security;

import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import com.InFrame.security.oauth2.CustomOAuth2User;
import com.InFrame.security.oauth2.CustomOAuth2UserService;
import com.InFrame.security.oauth2.OAuth2Attributes;
import com.InFrame.security.oauth2.OAuth2LoginFailureHandler;
import com.InFrame.security.oauth2.OAuth2LoginSuccessHandler;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired OAuth2LoginSuccessHandler successHandler;
    @Autowired OAuth2LoginFailureHandler failureHandler;
    @Autowired CustomOAuth2UserService oAuth2UserService;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdEmails.forEach(email -> userRepository.findByEmail(email).ifPresent(userRepository::delete));
    }

    private String uniqueEmail(String prefix) {
        String email = prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        createdEmails.add(email);
        return email;
    }

    @Test
    @DisplayName("회원가입 요청에 role=HOST 를 보내도 USER 로 가입된다")
    void signUp_withHostRole_isRegisteredAsUser() throws Exception {
        String email = uniqueEmail("signup");
        String body = """
                {"email":"%s","password":"pw1234!","nickname":"n%s","name":"테스터","role":"HOST"}
                """.formatted(email, UUID.randomUUID().toString().substring(0, 6));

        mockMvc.perform(post("/api/v1/auth/sign-up").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(email)).get()
                .extracting(User::getRole).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("OAuth 로그인 성공 시 토큰은 쿼리가 아닌 fragment 로 전달된다")
    void oauthSuccess_tokenInFragment() throws Exception {
        User user = userRepository.save(User.builder()
                .email(uniqueEmail("oauth")).password("").nickname("o" + UUID.randomUUID().toString().substring(0, 8))
                .name("테스터").role(Role.USER).provider("kakao").providerId("123456789").build());
        var auth = new TestingAuthenticationToken(new CustomOAuth2User(user, Map.of()), null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

        assertThat(response.getRedirectedUrl())
                .contains("#token=")
                .doesNotContain("?token=");
    }

    // ===== OAuth 계정 연결 =====

    private User saveLocalUser(String email) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").nickname("l" + UUID.randomUUID().toString().substring(0, 8))
                .name("로컬유저").role(Role.USER).build());
    }

    private static String errorCode(Throwable e) {
        return ((OAuth2AuthenticationException) e).getError().getErrorCode();
    }

    @Test
    @DisplayName("같은 이메일의 로컬 계정이 있으면 소셜 로그인으로 자동 연결하지 않는다")
    void oauth_sameEmailAsLocalAccount_isRejected() {
        String email = uniqueEmail("victim");
        User local = saveLocalUser(email);

        assertThatThrownBy(() -> oAuth2UserService.findOrRegister(
                new OAuth2Attributes("kakao", "kakao-" + UUID.randomUUID(), email, "공격자")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(CustomOAuth2UserService.EMAIL_ALREADY_REGISTERED));

        User reloaded = userRepository.findById(local.getId()).orElseThrow();
        assertThat(reloaded.getProvider()).isNull();
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 새로 가입하지 않고 기존 유저를 반환한다")
    void oauth_sameProviderAccount_returnsExistingUser() {
        String email = uniqueEmail("social");
        var attributes = new OAuth2Attributes("naver", "naver-" + UUID.randomUUID(), email, "소셜유저");

        User first = oAuth2UserService.findOrRegister(attributes);
        User second = oAuth2UserService.findOrRegister(attributes);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getRole()).isEqualTo(Role.USER);
        assertThat(first.getProvider()).isEqualTo("naver");
    }

    @Test
    @DisplayName("카카오 이메일 미동의 → email_required 오류")
    void kakao_withoutEmail_isRejected() {
        Map<String, Object> attributes = Map.of(
                "id", 123456789L,
                "kakao_account", Map.of("profile", Map.of("nickname", "닉네임")));

        assertThatThrownBy(() -> OAuth2Attributes.of("kakao", attributes))
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(OAuth2Attributes.EMAIL_REQUIRED));
    }

    @Test
    @DisplayName("네이버 이메일 누락 → NPE 가 아닌 email_required 오류")
    void naver_withoutEmail_isRejected() {
        Map<String, Object> attributes = Map.of("response", Map.of("id", "abc", "name", "이름"));

        assertThatThrownBy(() -> OAuth2Attributes.of("naver", attributes))
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(OAuth2Attributes.EMAIL_REQUIRED));
    }

    @Test
    @DisplayName("OAuth 로그인 실패 시 프론트 리다이렉트 URL 로 오류 코드를 전달한다")
    void oauthFailure_redirectsWithErrorCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error(CustomOAuth2UserService.EMAIL_ALREADY_REGISTERED)));

        assertThat(response.getRedirectedUrl()).endsWith("#error=" + CustomOAuth2UserService.EMAIL_ALREADY_REGISTERED);
    }
}
