package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StressTestCalculatorTest {
    private FinancialCalculator.Result evaluate(long cash, long loan) {
        return evaluate(cash, loan, 105, 50, 50, 0, 0);
    }

    private FinancialCalculator.Result evaluate(long cash, long loan, int sales, int rate, long rent, long overhead, long deposit) {
        var property = new PropertySnapshotV1(1, 1L, "shop", "address", null, 0d, 0d, BigDecimal.ONE,
                deposit, rent, 0L, 0L, null, MappingStatus.MAPPED, "A");
        var row = Map.of("commercialAreaCode", "A", "industryCode", "CS100001", "quarterCode", "20262",
                "reportedSalesAmountWon", sales, "similarIndustryStoreCount", 1);
        var condition = Map.<String, Object>of("availableCapital", cash, "loanAmount", loan, "monthlyLaborCost", 0,
                "monthlyOtherOperatingCost", overhead, "costOfSalesRate", rate, "seoulCommercialStatistics",
                Map.of("schemaVersion", 1, "capturedAt", "2026-06-30T00:00:00Z", "byCommercialAreaCode", Map.of("A", row)));
        return new PolicyFinancialCalculator(Optional.of(new MvpFinancialPolicy())).calculate(condition, List.of(property));
    }

    @Test
    void preservesBaselineAndCarriesRoundedCashThroughAllTwelveMonths() {
        var result = evaluate(6, 0);
        var base = result.properties().get(0);
        var scenarios = result.stressTests().get(1L);
        assertEquals(List.of(0, 10, 20, 30), scenarios.stream().map(StressTestResult::salesDecreasePercent).toList());
        assertEquals(base.safetyScore(), scenarios.get(0).safetyScore());
        assertEquals(base.safetyGrade(), scenarios.get(0).safetyGrade());
        assertNull(scenarios.get(0).depletionMonthIndex());
        for (var month : scenarios.get(0).months()) {
            assertEquals(52, month.variableCost());
            assertEquals(3, base.monthlyOperatingProfit());
            assertEquals(base.monthlyOperatingProfit(), month.netCashFlow());
        }
        // 105 x 90% = 94.5; variable cost = 47.25. Stored net = 95 - 47 - 50.
        var ten = scenarios.get(1);
        assertEquals(3, ten.depletionMonthIndex());
        assertEquals(95L, ten.months().get(0).sales());
        assertEquals(47L, ten.months().get(0).variableCost());
        assertEquals(-18L, ten.months().get(11).closingCash());
        for (var scenario : scenarios) {
            long previous = 6;
            assertEquals(12, scenario.months().size());
            for (int index = 0; index < 12; index++) {
                var m = scenario.months().get(index);
                assertEquals(index + 1, m.monthIndex());
                assertEquals(previous, m.openingCash());
                assertEquals(0, m.loanInterest());
                assertEquals(0, m.principalRepayment());
                assertEquals(m.sales() - m.variableCost() - m.fixedCost(), m.netCashFlow());
                assertEquals(previous + m.netCashFlow(), m.closingCash());
                previous = m.closingCash();
            }
        }
    }

    @Test
    void recordsOpeningDepletionEvenIfCashRecovers() {
        var baseline = evaluate(0, 0).stressTests().get(1L).get(0);
        assertEquals(0, baseline.depletionMonthIndex());
        assertTrue(baseline.months().get(11).closingCash() > 0);
    }

    @Test
    void negativeOpeningCashRecordsMonthZeroEvenAfterRecovery() {
        var scenario = evaluate(1, 0, 105, 50, 50, 0, 2).stressTests().get(1L).get(0);
        assertEquals(-1, scenario.months().get(0).openingCash());
        assertTrue(scenario.months().get(0).closingCash() > 0);
        assertEquals(0, scenario.depletionMonthIndex());
    }

    @Test
    void firstDepletionAtEndOfHorizonIsIncluded() {
        var scenario = evaluate(24, 0).stressTests().get(1L).get(1);
        assertEquals(2, scenario.months().get(10).closingCash());
        assertEquals(0, scenario.months().get(11).closingCash());
        assertEquals(12, scenario.depletionMonthIndex());
    }

    @Test
    void twentyPercentMeetsInclusiveGradeAMarginAndThirtyFallsToB() {
        var scenarios = evaluate(400, 0, 100, 0, 0, 64, 0).stressTests().get(1L);
        var twenty = scenarios.get(2);
        var thirty = scenarios.get(3);
        assertEquals(80, twenty.months().get(0).sales());
        assertEquals(16, twenty.months().get(0).netCashFlow());
        assertEquals("A", twenty.safetyGrade());
        assertEquals(70, thirty.months().get(0).sales());
        assertEquals(6, thirty.months().get(0).netCashFlow());
        assertEquals("B", thirty.safetyGrade());
    }

    @Test
    void loanKeepsExistingAnalysisWithoutDebtFreeScenarios() {
        var result = evaluate(6, 1);
        assertTrue(result.stressTests().isEmpty());
        assertFalse(result.assumptions().containsKey("stressTest"));
        assertNull(result.horizonMonths());
        assertEquals("MVP_FINANCIAL_V1", result.calculationVersion());
    }
}
