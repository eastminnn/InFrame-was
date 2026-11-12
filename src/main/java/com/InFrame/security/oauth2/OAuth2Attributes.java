package com.InFrame.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

/**
 * Provider 별로 다른 사용자 정보 응답을 공통 형태로 변환한다.
 * 카카오는 이메일 제공 동의가 선택 항목이라 email 이 null 일 수 있다.
 */
public record OAuth2Attributes(String provider, String providerId, String email, String name) {

    public static final String EMAIL_REQUIRED = "email_required";
    public static final String UNSUPPORTED_PROVIDER = "unsupported_provider";

    public static OAuth2Attributes of(String provider, Map<String, Object> attributes) {
        OAuth2Attributes parsed = switch (provider) {
            case "kakao" -> ofKakao(attributes);
            case "naver" -> ofNaver(attributes);
            default -> throw error(UNSUPPORTED_PROVIDER, "지원하지 않는 로그인 방식입니다.");
        };
        if (parsed.email() == null || parsed.email().isBlank()) {
            throw error(EMAIL_REQUIRED, "이메일 제공에 동의해야 가입할 수 있습니다.");
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static OAuth2Attributes ofKakao(Map<String, Object> attributes) {
        Map<String, Object> account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
        return new OAuth2Attributes(
                "kakao",
                String.valueOf(attributes.get("id")),
                (String) account.get("email"),
                (String) profile.get("nickname"));
    }

    @SuppressWarnings("unchecked")
    private static OAuth2Attributes ofNaver(Map<String, Object> attributes) {
        Map<String, Object> response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
        return new OAuth2Attributes(
                "naver",
                String.valueOf(response.get("id")),
                (String) response.get("email"),
                (String) response.get("name"));
    }

    static OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description);
    }
}
