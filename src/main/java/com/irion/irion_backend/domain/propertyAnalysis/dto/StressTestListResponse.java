package com.irion.irion_backend.domain.propertyAnalysis.dto;

import com.irion.irion_backend.domain.analysis.entity.StressTest;
import java.math.BigDecimal;
import java.util.List;

public record StressTestListResponse(Long propertyAnalysisId, List<Scenario> scenarios) {
    public record Scenario(Long stressTestId, int salesDeclineRate, String depletionStatus,
                           Integer depletionMonthIndex, BigDecimal safetyScore, String safetyGrade) {
        public static Scenario from(StressTest row) {
            return new Scenario(row.getId(), row.getSalesDecreasePercent(), row.getDepletionStatus(),
                    row.getDepletionMonthIndex(), row.getSafetyScore(), row.getSafetyGrade());
        }
    }
}
