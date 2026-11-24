package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "seoul-open-api.refresh-enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_ANALYSIS_SEOUL_LIVE", matches = "true")
class AnalysisCommercialStatisticsLiveTest {
    @Autowired AnalysisCommercialStatistics statistics;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsConfirmedObservationFromLocalIndustryAndPublishedStatistics() {
        Long id = jdbc.queryForObject("select id from industries where code = ?", Long.class, "CS100004");
        var property = mock(PropertySnapshotV1.class);
        when(property.commercialAreaCode()).thenReturn("3110621");
        var result = statistics.collect(Map.of("industryId", id), List.of(property));
        var byArea = (Map<?, ?>) result.get("byCommercialAreaCode");
        var row = (Map<?, ?>) byArea.get("3110621");
        assertEquals("20262", row.get("quarterCode"));
        assertEquals("CS100004", row.get("industryCode"));
        assertEquals(0, new BigDecimal("111952232").compareTo(new BigDecimal((String) row.get("reportedSalesAmountWon"))));
        assertEquals(3L, row.get("similarIndustryStoreCount"));
    }
}
