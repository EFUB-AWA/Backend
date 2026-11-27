package com.irion.irion_backend.domain.analysis.dto;

import java.math.BigDecimal;
import java.util.List;

public record StressTestResult(int salesDecreasePercent, BigDecimal safetyScore, String safetyGrade,
                              Integer depletionMonthIndex, List<Month> months) {
    public StressTestResult {
        months = List.copyOf(months);
    }

    public record Month(int monthIndex, long openingCash, long sales, long variableCost, long fixedCost,
                        long loanInterest, long principalRepayment, long netCashFlow, long closingCash) {}
}
