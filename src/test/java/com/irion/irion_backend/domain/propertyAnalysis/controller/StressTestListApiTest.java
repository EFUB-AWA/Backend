package com.irion.irion_backend.domain.propertyAnalysis.controller;

import com.irion.irion_backend.global.config.TestcontainersConfig;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import com.irion.irion_backend.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "seoul-open-api.refresh-enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
@Sql("/sql/property-consultation-test-data.sql")
class StressTestListApiTest {
    private static final String PATH = "/api/property-analyses/{id}/stress-tests";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TokenBlacklistRepository blacklist;

    private ResultActions fetch(long userId, long analysisId) throws Exception {
        return mvc.perform(get(PATH, analysisId).header("Authorization", "Bearer " + jwt.createAccessToken(userId).token()));
    }

    @Test
    void returnsOnlyStoredScenariosForRequestedAnalysisInRateOrder() throws Exception {
        jdbc.update("""
                insert into stress_tests(id, property_analysis_id, sales_decrease_percent, depletion_status,
                                         depletion_month_index, safety_score, safety_grade) values
                (11,9001,30,'DEPLETED',0,20,'C'), (12,9001,10,'DEPLETED',12,72.34,'B'),
                (13,9001,0,'NOT_DEPLETED',null,81,'A'), (14,9001,20,'DEPLETED',3,50,'C'),
                (15,9002,0,'NOT_DEPLETED',null,99,'A')
                """);
        fetch(9001,9001).andExpect(status().isOk()).andExpect(content().json("""
                {"propertyAnalysisId":9001,"scenarios":[
                  {"stressTestId":13,"salesDeclineRate":0,"depletionStatus":"NOT_DEPLETED","depletionMonthIndex":null,"safetyScore":81,"safetyGrade":"A"},
                  {"stressTestId":12,"salesDeclineRate":10,"depletionStatus":"DEPLETED","depletionMonthIndex":12,"safetyScore":72.34,"safetyGrade":"B"},
                  {"stressTestId":14,"salesDeclineRate":20,"depletionStatus":"DEPLETED","depletionMonthIndex":3,"safetyScore":50,"safetyGrade":"C"},
                  {"stressTestId":11,"salesDeclineRate":30,"depletionStatus":"DEPLETED","depletionMonthIndex":0,"safetyScore":20,"safetyGrade":"C"}
                ]}
                """, JsonCompareMode.STRICT));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(PATH,9001)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({"9002,9001,403,PROPERTY_ANALYSIS_ACCESS_DENIED", "9001,12345,404,PROPERTY_ANALYSIS_NOT_FOUND",
                "9001,9001,409,STRESS_TEST_NOT_READY"})
    void distinguishesForbiddenMissingAndUnprepared(long user, long analysis, int statusCode, String code) throws Exception {
        fetch(user,analysis).andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void mapsReadFailureToEndpointError() throws Exception {
        jdbc.execute("alter table stress_tests rename to unavailable_stress_tests");
        fetch(9001,9001).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STRESS_TEST_FETCH_FAILED"));
    }
}
