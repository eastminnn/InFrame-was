package com.InFrame.security.handler;

import com.InFrame.common.exception.error.ErrorCode;
import com.InFrame.common.exception.error.ErrorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계의 인증/인가 실패를 GlobalExceptionHandler 와 같은 ErrorDto 형식으로 응답한다.
 * (필터에서 발생한 실패는 @RestControllerAdvice 까지 도달하지 않기 때문)
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, e) -> write(response, ErrorCode.UNAUTHORIZED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> write(response, ErrorCode.FORBIDDEN_ACCESS);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorDto(errorCode.getStatus(), errorCode.getMessage()));
    }
}
