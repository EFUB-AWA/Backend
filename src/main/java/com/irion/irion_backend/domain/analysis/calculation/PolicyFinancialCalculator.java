package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.Evaluation;
import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.Financials;
import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.OpeningCost;
import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy.Scored;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("analysis-api")
public class PolicyFinancialCalculator implements FinancialCalculator {
    private final Optional<CalculationPolicy> policy;

    public PolicyFinancialCalculator(Optional<CalculationPolicy> policy) {
        this.policy = policy;
    }

    @Override
    public Result calculate(Map<String, Object> condition, List<PropertySnapshotV1> properties) {
        var ids = properties.stream().map(PropertySnapshotV1::candidatePropertyId).toList();
        var uniqueIds = Set.copyOf(ids);
        if (ids.isEmpty() || ids.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("Expected distinct candidate properties");
        }
        var rules = policy.orElseThrow(() -> new IllegalStateException("No approved calculation policy"))
                .evaluate(condition, properties);
        if (!rules.monthlySales().keySet().equals(uniqueIds)) {
            throw new IllegalArgumentException("Expected approved monthly sales for every requested property");
        }

        BigDecimal overhead = amount(condition.get("monthlyLaborCost"), "monthlyLaborCost")
                .add(amount(condition.get("monthlyOtherOperatingCost"), "monthlyOtherOperatingCost"));
        BigDecimal capital = amount(condition.get("availableCapital"), "availableCapital");
        BigDecimal rate = amount(condition.get("costOfSalesRate"), "costOfSalesRate");
        if (rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Cost rate exceeds 100 percent");
        }

        var ranked = properties.stream().map(property -> {
            var financials = calculateProperty(property, rules.monthlySales().get(property.candidatePropertyId()),
                    overhead, capital, rate, rules.openingCosts());
            return rules.assessment().apply(financials);
        }).sorted(rules.ranking()).toList();

        var rows = new HashMap<Long, CalculatedPropertyResult>();
        var assessments = new HashMap<String, Object>();
        var variableCosts = new HashMap<String, String>();
        var approvedSales = new HashMap<String, String>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored current = ranked.get(i);
            if (i > 0 && rules.ranking().compare(ranked.get(i - 1), current) == 0) {
                throw new IllegalArgumentException("Approved ranking rules must resolve ties");
            }
            Financials f = current.financials();
            if (!current.details().isEmpty()) assessments.put(Long.toString(f.id()), current.details());
            rows.put(f.id(), toResult(current, i + 1, rules.wonRounding()));
            approvedSales.put(Long.toString(f.id()), f.sales().toPlainString());
            variableCosts.put(Long.toString(f.id()), f.variableCost().toPlainString());
        }
        var assumptions = new HashMap<>(rules.assumptions());
        if (!assessments.isEmpty()) assumptions.put("stabilityByProperty", assessments);
        var audit = Map.of(
                "approvedMonthlySalesWonByProperty", approvedSales,
                "monthlyVariableCostWonByProperty", variableCosts,
                "openingCosts", rules.openingCosts().stream().map(Enum::name).sorted().toList(),
                "capitalBasis", "availableCapital", "wonRounding", rules.wonRounding().name());
        if (assumptions.putIfAbsent("financialCalculation", audit) != null) {
            throw new IllegalArgumentException("financialCalculation is reserved for the arithmetic audit");
        }
        var ordered = ids.stream().map(rows::get).toList();
        Object loan = condition.get("loanAmount");
        if (!MvpFinancialPolicy.VERSION.equals(rules.version()) || loan == null
                || new BigDecimal(loan.toString()).signum() != 0) {
            return new Result(ordered, Map.copyOf(assumptions), rules.version(), rules.dataCutoffAt(), rules.horizonMonths());
        }
        var scenarios = new HashMap<Long, List<StressTestResult>>();
        for (var property : properties) {
            long id = property.candidatePropertyId();
            scenarios.put(id, StressTestCalculator.calculate(rows.get(id), sales -> rules.assessment().apply(
                    calculateProperty(property, sales, overhead, capital, rate, rules.openingCosts()))));
        }
        assumptions.put("stressTest", Map.of("version", StressTestCalculator.VERSION,
                "assessmentPolicy", rules.version(), "horizonMonths", StressTestCalculator.MONTHS,
                "salesDecreasePercent", StressTestCalculator.DECREASES, "duration", "FULL_HORIZON",
                "loanBasis", "DEBT_FREE", "depletionThresholdWon", 0, "wonRounding", "HALF_UP"));
        return new Result(ordered, Map.copyOf(assumptions), rules.version() + "+" + StressTestCalculator.VERSION,
                rules.dataCutoffAt(), StressTestCalculator.MONTHS, Map.copyOf(scenarios));
    }

    private static Financials calculateProperty(PropertySnapshotV1 property, BigDecimal sales, BigDecimal overhead,
                                               BigDecimal capital, BigDecimal rate, Set<OpeningCost> openingCosts) {
        if (sales.signum() < 0) throw new IllegalArgumentException("Negative monthly sales");
        var occupancy = amount(property.monthlyRent(), "monthlyRent")
                .add(amount(property.monthlyManagementFee(), "monthlyManagementFee"));
        var fixed = overhead.add(occupancy);
        var fraction = rate.movePointLeft(2);
        var variable = sales.multiply(fraction);
        var profit = sales.subtract(variable).subtract(fixed);
        var margin = BigDecimal.ONE.subtract(fraction);
        BigDecimal threshold = null;
        if (fixed.signum() == 0) {
            threshold = BigDecimal.ZERO;
        } else if (margin.signum() > 0) {
            threshold = fixed.divide(margin, 0, RoundingMode.CEILING);
        }
        String status = CalculatedPropertyResult.UNREACHABLE;
        if (threshold != null) {
            status = profit.signum() >= 0 ? "ACHIEVED" : "NOT_ACHIEVED";
        }

        BigDecimal required = BigDecimal.ZERO;
        for (OpeningCost item : openingCosts) {
            required = required.add(switch (item) {
                case DEPOSIT -> amount(property.deposit(), "deposit");
                case KEY_MONEY -> amount(property.keyMoney(), "keyMoney");
            });
        }
        return new Financials(property.candidatePropertyId(), sales, fixed, variable, rate, profit,
                threshold, status, required, capital.subtract(required), occupancy);
    }

    private static CalculatedPropertyResult toResult(Scored scored, int rank, RoundingMode rounding) {
        var f = scored.financials();
        var row = new CalculatedPropertyResult(f.id(), won(f.sales(), rounding), won(f.fixedCost(), rounding),
                f.ratePercent(), won(f.profit(), rounding), f.breakEvenSales() == null ? null : f.breakEvenSales().longValueExact(),
                f.breakEvenStatus(), won(f.requiredCash(), rounding), won(f.operatingCash(), rounding), scored.score(),
                scored.band().grade(), rank, scored.band().reason());
        row.requireComplete();
        return row;
    }

    private static BigDecimal amount(Object value, String name) {
        if (value == null) throw new IllegalArgumentException("Missing " + name);
        var decimal = new BigDecimal(value.toString());
        if (decimal.signum() < 0) throw new IllegalArgumentException("Negative " + name);
        return decimal;
    }

    private static long won(BigDecimal amount, RoundingMode rounding) {
        return amount.setScale(0, rounding).longValueExact();
    }
}
