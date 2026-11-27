package com.irion.irion_backend.domain.aiConsultation.comparison.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.ANALYSIS_NOT_READY;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PropertySnapshot(Integer schemaVersion, Long analysisRunId, Long propertyAnalysisId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime analyzedAt, String calculationVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime dataCutoffAt,
        Map<String,Object> conditionSnapshot, Map<String,Object> assumptionsSnapshot,
        Long propertyId, String name, String address, String addressDetail, BigDecimal areaM2,
        Long deposit, Long monthlyRent, Long keyMoney, Long monthlyManagementFee, FinancialResult financialResult) {

    public record FinancialResult(Long expectedMonthlySales, Long monthlyFixedCost, BigDecimal variableCostRate,
            Long monthlyOperatingProfit, Long breakEvenSales, String breakEvenStatus, Long initialRequiredCash,
            Long initialOperatingCash, BigDecimal safetyScore, String safetyGrade, Integer recommendationRank,
            String recommendationReason) {}

    public void requireRetryInput() {
        if (propertyAnalysisId == null || propertyId == null || analyzedAt == null || dataCutoffAt == null
                || calculationVersion == null || calculationVersion.isBlank() || conditionSnapshot == null
                || assumptionsSnapshot == null || name == null || address == null || areaM2 == null
                || deposit == null || monthlyRent == null || keyMoney == null || monthlyManagementFee == null
                || financialResult == null) throw new BusinessException(ANALYSIS_NOT_READY);
        comparisonCondition(conditionSnapshot);
        var r = financialResult;
        try {
            new CalculatedPropertyResult(propertyId, r.expectedMonthlySales(), r.monthlyFixedCost(), r.variableCostRate(),
                    r.monthlyOperatingProfit(), r.breakEvenSales(), r.breakEvenStatus(), r.initialRequiredCash(),
                    r.initialOperatingCash(), r.safetyScore(), r.safetyGrade(), r.recommendationRank(),
                    r.recommendationReason()).requireComplete();
        } catch (NullPointerException incomplete) {
            throw new BusinessException(ANALYSIS_NOT_READY);
        }
    }

    public static PropertySnapshot capture(PropertyAnalysis p) {
        var run = p.getAnalysisRun();
        Long candidate = p.getCandidateProperty().getId();
        PropertySnapshotV1 contract = run.getPropertySnapshots().stream()
                .filter(s -> candidate.equals(s.candidatePropertyId())).findFirst()
                .orElseThrow(() -> new BusinessException(ANALYSIS_NOT_READY));
        var metrics = new CalculatedPropertyResult(candidate, p.getExpectedMonthlySales(), p.getMonthlyFixedCost(),
                p.getVariableCostRate(), p.getMonthlyOperatingProfit(), p.getBreakEvenSales(), p.getBreakEvenStatus(),
                p.getInitialRequiredCash(), p.getInitialOperatingCash(), p.getSafetyScore(), p.getSafetyGrade(),
                p.getRecommendationRank(), p.getRecommendationReason());
        try {
            metrics.requireComplete();
        } catch (NullPointerException missingMetric) {
            throw new BusinessException(ANALYSIS_NOT_READY);
        }
        if (run.getCompletedAt() == null || run.getDataCutoffAt() == null || run.getCalculationVersion() == null
                || run.getAssumptionsSnapshot() == null) throw new BusinessException(ANALYSIS_NOT_READY);
        var condition = new HashMap<>(run.getConditionSnapshot());
        String observed = frozenIndustryCode(condition, contract.commercialAreaCode());
        if (observed != null) {
            if (condition.containsKey("industryCode") && !observed.equals(condition.get("industryCode")))
                throw new BusinessException(ANALYSIS_NOT_READY);
            condition.put("industryCode", observed);
        }
        return new PropertySnapshot(2, run.getId(), p.getId(), run.getCompletedAt(), run.getCalculationVersion(),
                run.getDataCutoffAt(), comparisonCondition(condition), run.getAssumptionsSnapshot(), candidate, contract.name(),
                contract.address(), contract.addressDetail(), contract.areaM2(), contract.deposit(), contract.monthlyRent(),
                contract.keyMoney(), contract.monthlyManagementFee(), new FinancialResult(metrics.expectedMonthlySales(),
                metrics.monthlyFixedCost(), metrics.variableCostRate(), metrics.monthlyOperatingProfit(), metrics.breakEvenSales(),
                metrics.breakEvenStatus(), metrics.initialRequiredCash(), metrics.initialOperatingCash(), metrics.safetyScore(),
                metrics.safetyGrade(), metrics.recommendationRank(), metrics.recommendationReason()));
    }

    private static String frozenIndustryCode(Map<String,Object> condition, String area) {
        if (area == null || !condition.containsKey("seoulCommercialStatistics")) return null;
        if (!(condition.get("seoulCommercialStatistics") instanceof Map<?,?> statistics)
                || !(statistics.get("byCommercialAreaCode") instanceof Map<?,?> areas))
            throw new BusinessException(ANALYSIS_NOT_READY);
        if (!areas.containsKey(area)) return null;
        if (!(areas.get(area) instanceof Map<?,?> row) || !area.equals(row.get("commercialAreaCode"))
                || !(row.get("industryCode") instanceof String code) || !code.matches("CS[0-9]{6}"))
            throw new BusinessException(ANALYSIS_NOT_READY);
        return code;
    }

    /** Select and validate only historical values needed for comparison, before reserving a report. */
    public static Map<String,Object> comparisonCondition(Map<String,Object> source) {
        var result = new HashMap<String,Object>();
        if (!(source.get("preferredRegionCode") instanceof String region) || region.isBlank())
            throw new BusinessException(ANALYSIS_NOT_READY);
        result.put("preferredRegionCode", region);
        if (source.containsKey("industryCode")) {
            if (!(source.get("industryCode") instanceof String code) || !code.matches("CS[0-9]{6}"))
                throw new BusinessException(ANALYSIS_NOT_READY);
            result.put("industryCode", code);
        }
        for (String key : List.of("industryId", "totalCapital", "availableCapital", "loanAmount",
                "monthlyLaborCost", "monthlyOtherOperatingCost", "costOfSalesRate")) {
            Object value = source.get(key);
            try {
                if (!(value instanceof Number) && !(value instanceof String && key.equals("costOfSalesRate")))
                    throw new BusinessException(ANALYSIS_NOT_READY);
                var number = new BigDecimal(value.toString()).stripTrailingZeros();
                if (key.equals("costOfSalesRate")) result.put(key, number);
                else {
                    long integer = number.longValueExact();
                    if (key.equals("industryId") && integer <= 0) throw new BusinessException(ANALYSIS_NOT_READY);
                    result.put(key, integer);
                }
            } catch (NumberFormatException | ArithmeticException invalid) {
                throw new BusinessException(ANALYSIS_NOT_READY);
            }
        }
        return Map.copyOf(result);
    }
}
