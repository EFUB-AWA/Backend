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
@Sql(statements = """
        insert into stress_tests(id, property_analysis_id, sales_decrease_percent, depletion_status,
                                 depletion_month_index, safety_score, safety_grade) values
        (11,9001,30,'DEPLETED',0,31.25,'C'), (12,9001,0,'NOT_DEPLETED',null,85,'A');
        """)
class StressTestDetailApiTest {
    private static final String PATH = "/api/property-analyses/{analysis}/stress-tests/{scenario}";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TokenBlacklistRepository blacklist;

    private ResultActions fetch(long user, long analysis, long scenario) throws Exception {
        return mvc.perform(get(PATH, analysis, scenario)
                .header("Authorization", "Bearer " + jwt.createAccessToken(user).token()));
    }

    @Test
    void returnsStoredFieldsForSelectedScenario() throws Exception {
        fetch(9001,9001,11).andExpect(status().isOk()).andExpect(content().json("""
                {"stressTestId":11,"propertyAnalysisId":9001,"salesDeclineRate":30,"depletionStatus":"DEPLETED",
                 "depletionMonthIndex":0,"safetyScore":31.25,"safetyGrade":"C"}
                """, JsonCompareMode.STRICT));
        fetch(9001,9001,12).andExpect(status().isOk()).andExpect(content().json("""
                {"stressTestId":12,"propertyAnalysisId":9001,"salesDeclineRate":0,"depletionStatus":"NOT_DEPLETED",
                 "depletionMonthIndex":null,"safetyScore":85,"safetyGrade":"A"}
                """, JsonCompareMode.STRICT));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(PATH,9001,11)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({"9002,9001,11,403,PROPERTY_ANALYSIS_ACCESS_DENIED",
                "9002,9001,999,403,PROPERTY_ANALYSIS_ACCESS_DENIED",
                "9001,99999,11,404,PROPERTY_ANALYSIS_NOT_FOUND",
                "9001,9001,999,404,STRESS_TEST_NOT_FOUND",
                "9001,9002,11,400,STRESS_TEST_PROPERTY_MISMATCH"})
    void checksOwnershipBeforeScenarioExistenceAndMembership(long user, long analysis, long scenario,
                                                            int statusCode, String code) throws Exception {
        fetch(user,analysis,scenario).andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void returnsDetailErrorWhenScenarioReadFails() throws Exception {
        jdbc.execute("alter table stress_tests rename to unavailable_stress_tests");
        fetch(9001,9001,11).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STRESS_TEST_DETAIL_FETCH_FAILED"));
    }
}
