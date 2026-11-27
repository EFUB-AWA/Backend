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
        (11,9001,30,'DEPLETED',1,31.25,'C'), (12,9001,0,'NOT_DEPLETED',null,85,'A');
        """)
class StressTestCashFlowApiTest {
    private static final String PATH = "/api/stress-tests/{id}/cash-flows";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TokenBlacklistRepository blacklist;

    private ResultActions fetch(long user, long scenario) throws Exception {
        return mvc.perform(get(PATH, scenario)
                .header("Authorization", "Bearer " + jwt.createAccessToken(user).token()));
    }

    @Test
    void returnsStoredMonthsInOrderIncludingNegativeBalances() throws Exception {
        jdbc.update("""
                insert into monthly_cash_flows(stress_test_id,month_index,opening_cash,sales,variable_cost,
                        fixed_cost,loan_interest,principal_repayment,net_cash_flow,closing_cash) values
                (11,2,-5,100,31,80,4,6,-21,-26),
                (12,1,20,50,20,10,0,0,20,40),
                (11,1,3000000000,100,33,3000000062,3,7,-3000000005,-5)
                """);
        fetch(9001,11).andExpect(status().isOk()).andExpect(content().json("""
                {"stressTestId":11,"salesDeclineRate":30,"cashFlows":[
                  {"monthIndex":1,"openingCash":3000000000,"sales":100,"variableCost":33,"fixedCost":3000000062,
                   "loanInterest":3,"loanPrincipal":7,"netCashFlow":-3000000005,"closingCash":-5},
                  {"monthIndex":2,"openingCash":-5,"sales":100,"variableCost":31,"fixedCost":80,
                   "loanInterest":4,"loanPrincipal":6,"netCashFlow":-21,"closingCash":-26}]}
                """, JsonCompareMode.STRICT));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(PATH,11)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({"9002,11,403,STRESS_TEST_ACCESS_DENIED",
                "9001,999,404,STRESS_TEST_NOT_FOUND",
                "9001,11,404,CASH_FLOW_NOT_FOUND"})
    void distinguishesOwnershipAndMissingData(long user, long scenario, int statusCode, String code) throws Exception {
        fetch(user,scenario).andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void returnsCashFlowErrorWhenReadFails() throws Exception {
        jdbc.execute("alter table monthly_cash_flows rename to unavailable_cash_flows");
        fetch(9001,11).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASH_FLOW_FETCH_FAILED"));
    }
}
