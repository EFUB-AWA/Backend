package com.irion.irion_backend.domain.analysis.controller;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.calculation.FinancialCalculator;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunResponse;
import com.irion.irion_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode;
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

import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.json.JsonCompareMode;

import com.irion.irion_backend.domain.analysis.dto.AnalysisDetailResponse;
import com.irion.irion_backend.global.security.jwt.JwtProperties;
import com.irion.irion_backend.global.security.jwt.TokenBlacklistRepository;
import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.ANALYSIS_NOT_COMPLETED;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
                .andExpect(content().json("""
                        {"analysisRunId":100,"status":"PROCESSING"}
                        """, JsonCompareMode.STRICT));
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

    @Autowired private JwtProperties jwtProperties;
    @MockitoBean private TokenBlacklistRepository blacklist;

    private ResultActions detail(String id, String token) throws Exception {
        return mvc.perform(get(PATH + "/{id}", id).header("Authorization", "Bearer " + token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "C"})
    void returnsFullDetailIncludingNullFields(String grade) throws Exception {
        var condition = new AnalysisDetailResponse.StartupConditionSummary(3L, "11680", 100L, 70L, 30L, null);
        var property = new AnalysisDetailResponse.PropertySummary(20L, 10L, "매물", "주소", 80L, -5L,
                null, 60L, new BigDecimal("82.5"), grade, null, null);
        when(service.getDetail(7L, 100L)).thenReturn(new AnalysisDetailResponse(100L, null, AnalysisStatus.COMPLETED,
                null, null, null, condition, List.of(property)));
        detail("100", jwt.createAccessToken(7L).token()).andExpect(status().isOk())
                .andExpect(content().json("""
                        {"analysisRunId":100,"title":null,"status":"COMPLETED","completedAt":null,
                         "dataCutoffAt":null,"horizonMonths":null,"startupCondition":{"industryId":3,
                         "preferredRegionCode":"11680","totalCapital":100,"availableCapital":70,
                         "loanAmount":30,"annualInterestRate":null},"properties":[{"propertyAnalysisId":20,
                         "candidatePropertyId":10,"propertyName":"매물","address":"주소","expectedMonthlySales":80,
                         "monthlyOperatingProfit":-5,"breakEvenSales":null,"initialRequiredCash":60,
                         "safetyScore":82.5,"safetyGrade":"%s","recommendationRank":null,"recommendationReason":null}]}
                        """.formatted(grade), JsonCompareMode.STRICT));
    }

    @Test
    void detailRequiresActiveAccessToken() throws Exception {
        mvc.perform(get(PATH + "/100")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        var expired = new JwtProvider(new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-5),
                jwtProperties.refreshExpiration())).createAccessToken(7L);
        var loggedOut = jwt.createAccessToken(7L);
        when(blacklist.isBlacklisted(loggedOut.jti())).thenReturn(true);
        detail("100", "invalid").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
        detail("100", expired.token()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
        detail("100", loggedOut.token()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_LOGGED_OUT_TOKEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void rejectsInvalidDetailId(String id) throws Exception {
        detail(id, jwt.createAccessToken(7L).token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        verifyNoInteractions(service);
    }

    static Stream<ErrorCode> detailErrors() {
        return Stream.of(ANALYSIS_NOT_FOUND, ANALYSIS_ACCESS_DENIED, ANALYSIS_NOT_COMPLETED);
    }

    @ParameterizedTest
    @MethodSource("detailErrors")
    void preservesDetailError(ErrorCode error) throws Exception {
        when(service.getDetail(7L, 100L)).thenThrow(new BusinessException(error));
        detail("100", jwt.createAccessToken(7L).token()).andExpect(status().is(error.getStatus().value()))
                .andExpect(jsonPath("$.code").value(error.getCode()));
    }

    @Test
    void unexpectedDetailErrorUsesCommonResponse() throws Exception {
        when(service.getDetail(7L, 100L)).thenThrow(new IllegalStateException("Incomplete snapshot"));
        detail("100", jwt.createAccessToken(7L).token()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private ResultActions requestStatus(String id) throws Exception {
        return mvc.perform(get(PATH + "/{id}/status", id)
                .header("Authorization", "Bearer " + jwt.createAccessToken(7L).token()));
    }

    @ParameterizedTest
    @EnumSource(AnalysisStatus.class)
    void returnsStatusIncludingCompletionTime(AnalysisStatus state) throws Exception {
        var time = state == AnalysisStatus.COMPLETED ? LocalDateTime.of(2026, 11, 10, 12, 0) : null;
        when(service.getStatus(7L, 100L)).thenReturn(new AnalysisStatusResponse(100L, state, time));
        String expectedTime = state == AnalysisStatus.COMPLETED ? "\"2026-11-10T12:00:00\"" : "null";
        requestStatus("100").andExpect(status().isOk()).andExpect(content().json("""
                {"analysisRunId":100,"status":"%s","completedAt":%s}
                """.formatted(state, expectedTime), JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void rejectsInvalidStatusId(String id) throws Exception {
        requestStatus(id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        verifyNoInteractions(service);
    }

    @Test
    void statusRequiresValidToken() throws Exception {
        mvc.perform(get(PATH + "/100/status")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mvc.perform(get(PATH + "/100/status").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisErrorCode.class,
            names = {"ANALYSIS_ACCESS_DENIED", "ANALYSIS_NOT_FOUND"})
    void returnsStatusBusinessErrors(AnalysisErrorCode code) throws Exception {
        when(service.getStatus(7L, 100L)).thenThrow(new BusinessException(code));
        requestStatus("100").andExpect(status().is(code.getStatus().value())).andExpect(jsonPath("$.code").value(code.name()));
    }

    @Test
    void reportsUnexpectedLookupFailure() throws Exception {
        when(service.getStatus(7L, 100L)).thenThrow(new IllegalStateException("lookup failed"));
        requestStatus("100").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
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
