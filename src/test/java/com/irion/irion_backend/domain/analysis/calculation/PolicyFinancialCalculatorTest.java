package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.*;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PolicyFinancialCalculatorTest {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final Map<String, Object> condition = new HashMap<>(Map.of(
            "availableCapital", 99L, "loanAmount", 500L, "totalCapital", 1000L,
            "monthlyLaborCost", 10L, "monthlyOtherOperatingCost", 5L, "costOfSalesRate", "30.00"));

    private PropertySnapshotV1 property(long id, long rent, long fee) {
        return new PropertySnapshotV1(1, id, "p", "address", null, 0d, 0d, BigDecimal.ONE,
                100L, rent, 5L, fee, null, MappingStatus.MAPPED, "area");
    }

    private Evaluation rules(Map<Long, BigDecimal> sales, RoundingMode rounding, Set<OpeningCost> costs) {
        return new Evaluation(sales, costs, List.of(
                new ScoreComponent(BigDecimal.valueOf(60), f -> f.profit().signum() >= 0 ? HUNDRED : BigDecimal.ZERO),
                new ScoreComponent(BigDecimal.valueOf(40), f -> f.operatingCash().signum() >= 0 ? HUNDRED : BigDecimal.ZERO)),
                List.of(new GradeBand(BigDecimal.ZERO, "C", "fixture low"),
                        new GradeBand(BigDecimal.valueOf(60), "B", "fixture medium"),
                        new GradeBand(HUNDRED, "A", "fixture high")),
                Comparator.comparing(Scored::score).reversed().thenComparingLong(s -> s.financials().id()),
                Map.of("source", "test"), "fixture-v1", LocalDateTime.of(2025, 1, 1, 0, 0), 12, rounding);
    }

    private FinancialCalculator.Result calculate(Evaluation rules, PropertySnapshotV1... properties) {
        CalculationPolicy policy = (frozen, candidates) -> rules;
        return new PolicyFinancialCalculator(Optional.of(policy)).calculate(condition, List.of(properties));
    }

    private FinancialCalculator.Result single(String sales, RoundingMode rounding) {
        return calculate(rules(Map.of(1L, new BigDecimal(sales)), rounding, Set.of(OpeningCost.DEPOSIT, OpeningCost.KEY_MONEY)),
                property(1, 20, 5));
    }

    @Test
    void usesSnapshotCostsCapitalAndApprovedWeightedGradesInRequestOrder() {
        var result = calculate(rules(Map.of(2L, BigDecimal.ZERO, 1L, HUNDRED), RoundingMode.HALF_UP,
                Set.of(OpeningCost.DEPOSIT, OpeningCost.KEY_MONEY)), property(2, 20, 5), property(1, 20, 5));
        var loss = result.properties().get(0);
        var gain = result.properties().get(1);
        assertEquals(2L, loss.candidatePropertyId());
        assertEquals(2, loss.recommendationRank());
        assertEquals("C", loss.safetyGrade());
        assertEquals(-40L, loss.monthlyOperatingProfit());
        assertEquals(1, gain.recommendationRank());
        assertEquals("B", gain.safetyGrade());
        assertEquals(0, gain.safetyScore().compareTo(BigDecimal.valueOf(60)));
        assertEquals(40L, gain.monthlyFixedCost());
        assertEquals(30L, gain.monthlyOperatingProfit());
        assertEquals(58L, gain.breakEvenSales());
        assertEquals("ACHIEVED", gain.breakEvenStatus());
        assertEquals(105L, gain.initialRequiredCash());
        assertEquals(-6L, gain.initialOperatingCash());
        assertEquals("fixture-v1", result.calculationVersion());
        assertEquals("test", result.assumptions().get("source"));
        var audit = (Map<?, ?>) result.assumptions().get("financialCalculation");
        var costs = (Map<?, ?>) audit.get("monthlyVariableCostWonByProperty");
        assertEquals(0, new BigDecimal((String) costs.get("1")).compareTo(BigDecimal.valueOf(30)));
    }

    @Test
    void availableCapitalAloneFundsOpeningAndPlannedLoansDoNotAlterOperatingProfit() {
        condition.put("costOfSalesRate", "0.30");
        var baseline = single("10000", RoundingMode.UNNECESSARY).properties().get(0);
        assertEquals(9930L, baseline.monthlyOperatingProfit()); // 10,000 - 30 (0.30%) - 40
        assertEquals(-6L, baseline.initialOperatingCash());     // 99 - 105

        condition.put("totalCapital", 2000L);
        assertEquals(baseline, single("10000", RoundingMode.UNNECESSARY).properties().get(0));
        condition.put("loanAmount", 0L);
        assertEquals(baseline, single("10000", RoundingMode.UNNECESSARY).properties().get(0));
        condition.put("availableCapital", 150L);
        var funded = single("10000", RoundingMode.UNNECESSARY).properties().get(0);
        assertEquals(45L, funded.initialOperatingCash());
        assertEquals(baseline.monthlyOperatingProfit(), funded.monthlyOperatingProfit());
    }

    @Test
    void zeroRateAndExactBreakEven() {
        condition.put("costOfSalesRate", 0);
        var row = single("40", RoundingMode.UNNECESSARY).properties().get(0);
        assertEquals(40L, row.breakEvenSales());
        assertEquals(0L, row.monthlyOperatingProfit());
        assertEquals("ACHIEVED", row.breakEvenStatus());
    }

    @Test
    void fullRateDistinguishesUnreachableFromZeroFixedCost() {
        condition.put("costOfSalesRate", "100");
        var row = single("100", RoundingMode.UNNECESSARY).properties().get(0);
        assertNull(row.breakEvenSales());
        assertEquals("UNREACHABLE", row.breakEvenStatus());
        assertDoesNotThrow(row::requireComplete);
        assertEquals(-40L, row.monthlyOperatingProfit());
        condition.put("monthlyOtherOperatingCost", 0);
        condition.put("monthlyLaborCost", 0);
        var zero = calculate(rules(Map.of(1L, BigDecimal.ZERO), RoundingMode.DOWN, Set.of(OpeningCost.DEPOSIT)),
                property(1, 0, 0)).properties().get(0);
        assertEquals(0L, zero.breakEvenSales());
        assertEquals("ACHIEVED", zero.breakEvenStatus());
        assertEquals(100L, zero.initialRequiredCash());
    }

    @Test
    void roundsOnlyAfterSubtractingExactCostsAndDetectsOverflow() {
        var halfUp = single("57.8", RoundingMode.HALF_UP).properties().get(0);
        var down = single("57.8", RoundingMode.DOWN).properties().get(0);
        assertEquals(58L, halfUp.expectedMonthlySales());
        assertEquals(57L, down.expectedMonthlySales());
        assertEquals("ACHIEVED", halfUp.breakEvenStatus());
        assertEquals("NOT_ACHIEVED", single("57.1", RoundingMode.HALF_UP).properties().get(0).breakEvenStatus());
        assertEquals(0L, halfUp.monthlyOperatingProfit());
        assertEquals(-39L, single("1.49", RoundingMode.HALF_UP).properties().get(0).monthlyOperatingProfit());
        assertEquals(-39L, single("0.75", RoundingMode.HALF_UP).properties().get(0).monthlyOperatingProfit());
        assertEquals(-40L, single("0.70", RoundingMode.HALF_UP).properties().get(0).monthlyOperatingProfit());
        assertThrows(ArithmeticException.class, () -> single("9223372036854775808", RoundingMode.HALF_UP));
    }

    @Test
    void ranksTiedScoresByTheInjectedOrderAndRejectsUnresolvedTies() {
        var sales = Map.of(1L, HUNDRED, 2L, HUNDRED);
        var rules = rules(sales, RoundingMode.HALF_UP, Set.of(OpeningCost.DEPOSIT));
        var first = property(1, 20, 5);
        var second = property(2, 20, 5);
        var ranked = calculate(rules, second, first).properties();
        assertEquals(2, ranked.get(0).recommendationRank());
        assertEquals(1, ranked.get(1).recommendationRank());
        var noTieBreak = new Evaluation(sales, rules.openingCosts(), rules.assessment(),
                Comparator.comparing(Scored::score), rules.assumptions(), rules.version(),
                rules.dataCutoffAt(), rules.horizonMonths(), rules.wonRounding());
        assertThrows(IllegalArgumentException.class, () -> calculate(noTieBreak, second, first));
    }

    @ParameterizedTest
    @ValueSource(strings = {"monthlyLaborCost", "monthlyOtherOperatingCost", "costOfSalesRate", "availableCapital"})
    void rejectsMissingOrNegativeInputs(String field) {
        condition.remove(field);
        assertThrows(IllegalArgumentException.class, () -> single("100", RoundingMode.DOWN));
        condition.put(field, -1);
        assertThrows(IllegalArgumentException.class, () -> single("100", RoundingMode.DOWN));
    }

    @Test
    void rejectsOutOfRangeRateMissingSalesAndUnapprovedPolicy() {
        assertThrows(IllegalStateException.class, () -> new PolicyFinancialCalculator(Optional.empty())
                .calculate(condition, List.of(property(1, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> calculate(rules(Map.of(), RoundingMode.DOWN, Set.of()),
                property(1, 0, 0)));
        condition.put("costOfSalesRate", "100.01");
        assertThrows(IllegalArgumentException.class, () -> single("100", RoundingMode.DOWN));
    }
}
