package com.InFrame.security.oauth2;

import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public static final String EMAIL_ALREADY_REGISTERED = "email_already_registered";

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Attributes attributes = OAuth2Attributes.of(provider, oAuth2User.getAttributes());

        return new CustomOAuth2User(findOrRegister(attributes), oAuth2User.getAttributes());
    }

    /**
     * 같은 소셜 계정(provider + providerId)이면 로그인, 처음이면 가입.
     * 이메일이 같은 다른 계정(로컬 가입 또는 다른 소셜)이 있으면 자동 연결하지 않는다.
     * → 소셜 계정 이메일만으로 남의 계정에 로그인되는 것을 막음
     */
    @Transactional
    public User findOrRegister(OAuth2Attributes attributes) {
        return userRepository.findByProviderAndProviderId(attributes.provider(), attributes.providerId())
                .orElseGet(() -> register(attributes));
    }

    private User register(OAuth2Attributes attributes) {
        if (userRepository.existsByEmail(attributes.email())) {
            throw OAuth2Attributes.error(EMAIL_ALREADY_REGISTERED, "이미 다른 방식으로 가입된 이메일입니다.");
        }

        String providerId = attributes.providerId();
        String nickname = attributes.provider() + "_" + providerId.substring(0, Math.min(6, providerId.length()));
        while (userRepository.existsByNickname(nickname)) {
            nickname += (int) (Math.random() * 10);
        }

        return userRepository.save(User.builder()
                .email(attributes.email())
                .password("")
                .nickname(nickname)
                .name(attributes.name() != null ? attributes.name() : nickname)
                .role(Role.USER)
                .provider(attributes.provider())
                .providerId(providerId)
                .build());
    }
}
