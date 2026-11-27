package com.irion.irion_backend.domain.propertyAnalysis.dto;

import com.irion.irion_backend.domain.analysis.entity.StressTest;
import java.math.BigDecimal;

public record StressTestDetailResponse(Long stressTestId, Long propertyAnalysisId, int salesDeclineRate,
                                     String depletionStatus, Integer depletionMonthIndex, BigDecimal safetyScore,
                                     String safetyGrade) {
    public static StressTestDetailResponse from(StressTest row) {
        return new StressTestDetailResponse(row.getId(), row.getPropertyAnalysis().getId(), row.getSalesDecreasePercent(),
                row.getDepletionStatus(), row.getDepletionMonthIndex(), row.getSafetyScore(), row.getSafetyGrade());
    }
}
