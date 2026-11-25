package com.irion.irion_backend.domain.analysis.controller;

import com.irion.irion_backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerDisabledTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwt;

    @Test
    void defaultProfileHasNoStatusEndpoint() throws Exception {
        mvc.perform(get("/api/analyses/100/status").header("Authorization", "Bearer " + jwt.createAccessToken(7L).token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("API_NOT_FOUND"));
    }

    @Test
    void defaultProfileHasNoAnalysisEndpoint() throws Exception {
        mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + jwt.createAccessToken(7L).token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("API_NOT_FOUND"));
    }
}
