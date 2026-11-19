package com.InFrame.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClientErrorStatusTest {

    @Autowired MockMvc mockMvc;

    static Stream<Arguments> clientErrors() {
        return Stream.of(
                Arguments.of("없는 경로", get("/api/v1/enums/nope"), 404),
                Arguments.of("필수 파라미터 누락", get("/api/v1/experiences/1/available-slots"), 400),
                Arguments.of("파라미터 타입 오류", get("/api/v1/experiences/1/available-slots").param("date", "abc"), 400),
                Arguments.of("경로 변수 타입 오류", get("/api/v1/experiences/abc/available-slots").param("date", "2026-12-01"), 400),
                Arguments.of("깨진 JSON", post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content("{bad"), 400),
                Arguments.of("지원하지 않는 Content-Type", post("/api/v1/auth/sign-in").contentType(MediaType.TEXT_PLAIN).content("x"), 415),
                Arguments.of("지원하지 않는 메서드", put("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content("{}"), 405)
        );
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource("clientErrors")
    @DisplayName("잘못된 클라이언트 요청은 500이 아니라 해당 4xx 로 응답")
    void clientError_returns4xx(String name, RequestBuilder request, int expectedStatus) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    @DisplayName("405 응답에는 허용 메서드(Allow 헤더)가 포함된다")
    void methodNotAllowed_includesAllowHeader() throws Exception {
        mockMvc.perform(put("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"));
    }
}
