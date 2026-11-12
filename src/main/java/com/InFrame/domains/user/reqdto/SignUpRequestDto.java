package com.InFrame.domains.user.reqdto;


import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청 DTO")
public record SignUpRequestDto(
        @Schema(description = "사용자 이메일", example = "abcd1234@gmail.com")
        @Email(message = "이메일 형식을 맞춰주세요.")
        @NotBlank(message = "이메일은 필수 입력입니다.")
        String email,

        @Schema(description = "비밀번호", example = "abcd1234")
        @NotBlank(message = "비밀번호는 필수 입력입니다.")
        String password,

        @Schema(description = "사용자 닉네임", example = "열정있는 아기사자")
        @NotBlank(message = "닉네임은 필수 입력입니다.")
        @Size(max = 10, message = "닉네임은 최대 10글자 까지 가능합니다.")
        String nickname,

        @Schema(description = "사용자 이름", example = "홍길동")
        @NotBlank(message = "이름은 필수 입력입니다.")
        String name
) {
    // 가입은 항상 USER. 호스트 전환은 사업자번호 검증을 거치는 /api/v1/host/update 로만 가능
    public User toEntity(String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .name(name)
                .role(Role.USER)
                .build();
    }
}
