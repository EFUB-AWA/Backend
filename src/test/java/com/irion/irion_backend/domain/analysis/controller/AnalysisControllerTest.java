package com.irion.irion_backend.domain.analysis.controller;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.calculation.FinancialCalculator;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunResponse;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.error.ErrorCode;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.stream.Stream;

import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.*;
import static com.irion.irion_backend.domain.property.exception.PropertyErrorCode.PROPERTY_NOT_FOUND;
import static com.irion.irion_backend.domain.startupCondition.exception.StartupConditionErrorCode.STARTUP_CONDITION_NOT_FOUND;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("analysis-api")
@AutoConfigureMockMvc
class AnalysisControllerTest {
    private static final String PATH = "/api/analyses";
    private static final String BODY = """
            {"startupConditionId":1,"candidatePropertyIds":[10,11],"title":"run"}
            """;
    private final AnalysisRunRequest request = new AnalysisRunRequest(1L, List.of(10L, 11L), "run");

    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwt;
    @MockitoBean private AnalysisService service;
    @MockitoBean private FinancialCalculator calculator;

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(post(PATH).header("Authorization", "Bearer " + jwt.createAccessToken(7L).token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void acceptsAuthenticatedRequest() throws Exception {
        when(service.start(7L, request)).thenReturn(new AnalysisRunResponse(100L, AnalysisStatus.PROCESSING));
        submit(BODY).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisRunId").value(100))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{}", "{\"candidatePropertyIds\":[10]}", "{\"startupConditionId\":1}",
            "{\"candidatePropertyIds\":[]}", "{\"startupConditionId\":1,\"candidatePropertyIds\":[null]}"})
    void rejectsInvalidBody(String body) throws Exception {
        submit(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANALYSIS_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsEmptyList() throws Exception {
        submit("{\"startupConditionId\":1,\"candidatePropertyIds\":[]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_PROPERTY_LIST"));
        verifyNoInteractions(service);
    }

    static Stream<ErrorCode> validationErrors() {
        return Stream.of(ANALYSIS_ACCESS_DENIED, STARTUP_CONDITION_NOT_FOUND, PROPERTY_NOT_FOUND,
                PROPERTY_MAPPING_NOT_COMPLETED);
    }

    @ParameterizedTest
    @MethodSource("validationErrors")
    void preservesBusinessError(ErrorCode code) throws Exception {
        when(service.start(7L, request)).thenThrow(new BusinessException(code));
        submit(BODY).andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeValidation() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void unrelatedControllerKeepsGlobalBodyError() throws Exception {
        mvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(7L).token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }
}
