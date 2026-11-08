package com.irion.irion_backend.domain.auth.dev.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("local")
@AutoConfigureMockMvc
public class DevTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 개발용 토큰 발급 및 해당 토큰으로 /api/dev/me 호출
    @Test
    void 개발용_토큰_발급_및_토큰으로_userId_받기() throws Exception {
        String body = mockMvc.perform(post("/api/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 1}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/dev/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    // userId 없이 토큰 발급 요청
    @Test
    void userId_없이_토큰_발급_요청_시_400() throws Exception {
        mockMvc.perform(post("/api/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}