package com.irion.irion_backend.domain.analysis.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record CalculatedPropertyResult(
        Long candidatePropertyId,
        Long expectedMonthlySales,
        Long monthlyFixedCost,
        BigDecimal variableCostRate,
        Long monthlyOperatingProfit,
        Long breakEvenSales,
        String breakEvenStatus,
        Long initialRequiredCash,
        Long initialOperatingCash,
        BigDecimal safetyScore,
        String safetyGrade,
        Integer recommendationRank,
        String recommendationReason
) {
    public static final String UNREACHABLE = "UNREACHABLE";

    public void requireComplete() {
        Objects.requireNonNull(candidatePropertyId, "candidatePropertyId");
        Objects.requireNonNull(expectedMonthlySales, "expectedMonthlySales");
        Objects.requireNonNull(monthlyFixedCost, "monthlyFixedCost");
        Objects.requireNonNull(variableCostRate, "variableCostRate");
        Objects.requireNonNull(monthlyOperatingProfit, "monthlyOperatingProfit");
        if (!UNREACHABLE.equals(breakEvenStatus)) {
            Objects.requireNonNull(breakEvenSales, "breakEvenSales");
        }
        Objects.requireNonNull(breakEvenStatus, "breakEvenStatus");
        Objects.requireNonNull(initialRequiredCash, "initialRequiredCash");
        Objects.requireNonNull(initialOperatingCash, "initialOperatingCash");
        Objects.requireNonNull(safetyScore, "safetyScore");
        Objects.requireNonNull(safetyGrade, "safetyGrade");
        Objects.requireNonNull(recommendationRank, "recommendationRank");
        Objects.requireNonNull(recommendationReason, "recommendationReason");
    }
}
