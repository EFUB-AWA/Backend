package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.Financials;
import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.Scored;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class MvpFinancialPolicyTest {
    private final MvpFinancialPolicy policy = new MvpFinancialPolicy();

    // Sales of 100, no variable cost. Thus fixed costs of 95 give exactly 5% sales headroom.
    private Scored assess(long cash, long fixed, long occupancy) {
        return assess(1, 100, cash, fixed, occupancy);
    }

    private Scored assess(long id, long sales, long cash, long fixed, long occupancy) {
        var f = new Financials(id, BigDecimal.valueOf(sales), BigDecimal.valueOf(fixed), ZERO, ZERO,
                BigDecimal.valueOf(sales - fixed), BigDecimal.valueOf(fixed),
                sales >= fixed ? "ACHIEVED" : "NOT_ACHIEVED", ZERO, BigDecimal.valueOf(cash), BigDecimal.valueOf(occupancy));
        return policy.assess(f);
    }

    @ParameterizedTest
    @CsvSource({"0,0.00", "50,6.67", "150,20.00", "300,40.00", "350,40.00"})
    void fundingAtZeroOneThreeAndSixMonths(long cash, String points) {
        assertEquals(new BigDecimal(points), assess(cash, 50, 10).details().get("fundingScore"));
    }

    @ParameterizedTest
    @CsvSource({"100,0.00", "95,6.67", "80,26.67", "70,40.00", "60,40.00"})
    void safetyAtMarginBoundaries(long fixed, String points) {
        assertEquals(new BigDecimal(points), assess(1000, fixed, 10).details().get("breakEvenMarginScore"));
    }

    @ParameterizedTest
    @CsvSource({"10,20.00", "15,13.33", "20,6.67", "25,0.00", "26,0.00"})
    void rentBurdenThresholds(long occupancy, String points) {
        assertEquals(new BigDecimal(points), assess(1000, 50, occupancy).details().get("rentBurdenScore"));
    }

    @ParameterizedTest
    @CsvSource({"210,70,80.00,A", "209,70,79.90,B", "400,80,80.00,A", "475,95,60.00,B",
            "474,95,59.93,C", "170,85,53.33,C", "600,81,85.33,B", "49,50,66.53,C",
            "600,96,65.33,C"})
    void gradesRequireBothUnroundedScoreAndFinancialGates(long cash, long fixed, String score, String grade) {
        var result = assess(cash, fixed, 10);
        assertEquals(new BigDecimal(score), result.score());
        assertEquals(grade, result.band().grade());
    }

    @Test
    void overridesKeepScoreAndBothReasonsWhenApplicable() {
        var unfunded = assess(-1, 50, 10);
        assertEquals("C", unfunded.band().grade());
        assertEquals(new BigDecimal("60.00"), unfunded.score());
        assertEquals(1, ((List<?>) unfunded.details().get("gradeRestrictions")).size());
        var zeroProfit = assess(600, 100, 10);
        assertEquals("C", zeroProfit.band().grade());
        assertEquals(new BigDecimal("60.00"), zeroProfit.score());
        assertEquals(1, ((List<?>) zeroProfit.details().get("gradeRestrictions")).size());
        assertEquals(2, ((List<?>) assess(-1, 110, 10).details().get("gradeRestrictions")).size());
    }

    @Test
    void zeroFixedCostAndZeroSales() {
        assertEquals("A", assess(0, 0, 0).band().grade());
        assertEquals(new BigDecimal("0.00"), assess(-1, 0, 0).details().get("fundingScore"));
        var noSales = assess(1, 0, 300, 50, 10);
        assertEquals(new BigDecimal("40.00"), noSales.score());
        assertEquals("C", noSales.band().grade());
    }

    @Test
    void ignoresRoundedBreakEvenWhenScoringAndHandlesUnreachable() {
        var fractionalThreshold = new Financials(1, BigDecimal.TEN, BigDecimal.valueOf(6), BigDecimal.valueOf(2),
                BigDecimal.valueOf(20), BigDecimal.valueOf(2), BigDecimal.valueOf(8), "ACHIEVED", ZERO, ZERO, ZERO);
        assertEquals(new BigDecimal("33.33"), policy.assess(fractionalThreshold).details().get("breakEvenMarginScore"));
        var unreachable = new Financials(1, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(100),
                BigDecimal.ONE.negate(), null, "UNREACHABLE", ZERO, ZERO, ZERO);
        assertEquals(new BigDecimal("0.00"), policy.assess(unreachable).details().get("breakEvenMarginScore"));
    }

    @Test
    void ranksByGradeScoreRunwayProfitAndId() {
        var ranked = List.of(assess(4, 110, 300, 50, 10), assess(3, 110, 300, 50, 10),
                assess(2, 100, 300, 50, 10), assess(1, 100, 301, 50, 10), assess(5, 100, -1, 50, 10),
                assess(6, 100, 475, 95, 10), assess(7, 100, 0, 0, 0), assess(8, 100, 600, 81, 10))
                .stream().sorted(MvpFinancialPolicy.RANKING).map(s -> s.financials().id()).toList();
        assertEquals(List.of(7L, 1L, 3L, 4L, 2L, 8L, 6L, 5L), ranked);
    }

    @Test
    void enginePreservesRequestOrderUsesFrozenCutoffAndStoresPerPropertyReasons() {
        var first = property(1, 0);
        var second = property(2, 1000);
        var observation = Map.of("commercialAreaCode", "A", "quarterCode", "20262", "industryCode", "CS100004",
                "reportedSalesAmountWon", "111952232", "similarIndustryStoreCount", 3);
        var condition = Map.<String, Object>of("availableCapital", 100, "loanAmount", 10000, "monthlyLaborCost", 10,
                "monthlyOtherOperatingCost", 5, "costOfSalesRate", 20, "seoulCommercialStatistics",
                Map.of("schemaVersion", 1, "capturedAt", "2026-06-30T01:00:00Z", "byCommercialAreaCode", Map.of("A", observation)));
        var result = new PolicyFinancialCalculator(Optional.of(policy)).calculate(condition, List.of(second, first));
        var rows = result.properties();
        assertEquals(List.of(2L, 1L), rows.stream().map(r -> r.candidatePropertyId()).toList());
        assertEquals(2, rows.get(0).recommendationRank());
        assertEquals(-900L, rows.get(0).initialOperatingCash());
        assertEquals("C", rows.get(0).safetyGrade());
        assertEquals(37_317_411L, rows.get(1).expectedMonthlySales());
        assertEquals(LocalDateTime.of(2026, 6, 30, 1, 0), result.dataCutoffAt());
        assertEquals("MVP_FINANCIAL_V1", result.calculationVersion());
        assertEquals(2, ((Map<?, ?>) result.assumptions().get("stabilityByProperty")).size());
    }

    private PropertySnapshotV1 property(long id, long deposit) {
        return new PropertySnapshotV1(1, id, "p", "address", null, 0d, 0d, BigDecimal.ONE,
                deposit, 0L, 0L, 0L, null, MappingStatus.MAPPED, "A");
    }
}
