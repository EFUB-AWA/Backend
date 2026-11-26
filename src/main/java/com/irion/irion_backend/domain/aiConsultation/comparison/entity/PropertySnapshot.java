package com.irion.irion_backend.domain.aiConsultation.comparison.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PropertySnapshot(
        Long propertyId,
        String name,
        String address,
        String addressDetail,
        BigDecimal areaM2,
        Long deposit,
        Long monthlyRent,
        Long keyMoney,
        Long monthlyManagementFee,
        FinancialResult financialResult
) {
    public record FinancialResult(
            Long expectedMonthlySales,
            Long monthlyOperatingProfit,
            Long breakEvenSales,
            String breakEvenStatus,
            Long initialRequiredCash,
            Long initialOperatingCash,
            BigDecimal safetyScore,
            String safetyGrade,
            Integer recommendationRank,
            String recommendationReason
    ) {}
}
