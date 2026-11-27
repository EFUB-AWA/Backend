package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import com.irion.irion_backend.domain.analysis.dto.StressTestResult.Month;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class StressTestCalculator {
    public static final int MONTHS = 12;
    public static final List<Integer> DECREASES = List.of(0, 10, 20, 30);
    public static final String VERSION = "STRESS_V1";

    private StressTestCalculator() {}

    static List<StressTestResult> calculate(CalculatedPropertyResult base,
                                            Function<BigDecimal, CalculationPolicy.Scored> assessSales) {
        return DECREASES.stream().map(decrease -> {
            var sales = BigDecimal.valueOf(base.expectedMonthlySales())
                    .multiply(BigDecimal.valueOf(100 - decrease, 2));
            var variable = sales.multiply(base.variableCostRate()).movePointLeft(2);
            long salesWon = won(sales);
            // Assign the baseline's rounding residual to variable cost so the stored profit reconciles.
            long variableWon = decrease == 0
                    ? Math.subtractExact(Math.subtractExact(salesWon, base.monthlyFixedCost()), base.monthlyOperatingProfit())
                    : won(variable);
            // Only explicitly debt-free snapshots enter this calculation.
            long net = Math.subtractExact(Math.subtractExact(salesWon, variableWon), base.monthlyFixedCost());
            long cash = base.initialOperatingCash();
            Integer depletion = cash <= 0 ? 0 : null;
            var months = new ArrayList<Month>(MONTHS);
            for (int month = 1; month <= MONTHS; month++) {
                long closing = Math.addExact(cash, net);
                months.add(new Month(month, cash, salesWon, variableWon, base.monthlyFixedCost(),
                        0, 0, net, closing));
                if (depletion == null && closing <= 0) depletion = month;
                cash = closing;
            }
            var score = base.safetyScore();
            var grade = base.safetyGrade();
            if (decrease > 0) {
                var assessment = assessSales.apply(sales);
                score = assessment.score();
                grade = assessment.band().grade();
            }
            return new StressTestResult(decrease, score, grade, depletion, months);
        }).toList();
    }

    private static long won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
