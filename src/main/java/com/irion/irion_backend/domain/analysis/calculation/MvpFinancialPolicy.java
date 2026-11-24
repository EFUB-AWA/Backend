package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

@Component
@Profile("analysis-api")
public class MvpFinancialPolicy implements CalculationPolicy {
    static final String VERSION = "MVP_FINANCIAL_V1";
    private static final AreaAverageMonthlySales MONTHLY_SALES = new AreaAverageMonthlySales(16, HALF_UP);
    private static final BigDecimal SIX_MONTHS = BigDecimal.valueOf(6);
    private static final BigDecimal FULL_MARGIN = new BigDecimal("0.30");
    private static final BigDecimal LOW_RENT = new BigDecimal("0.10");
    private static final BigDecimal HIGH_RENT = new BigDecimal("0.25");
    private static final BigDecimal FORTY = BigDecimal.valueOf(40);
    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);
    private static final GradeBand A = new GradeBand(BigDecimal.valueOf(80), "A", "A등급 점수·자금 여력·매출 안전 여유 기준 충족");
    private static final GradeBand B = new GradeBand(BigDecimal.valueOf(60), "B", "B등급 점수·자금 여력·매출 안전 여유 기준 충족");
    private static final GradeBand C = new GradeBand(ZERO, "C", "A·B등급 기준 미충족");

    @Override
    public Evaluation evaluate(Map<String, Object> condition, List<PropertySnapshotV1> properties) {
        var sales = MONTHLY_SALES.estimate(condition, properties);
        var statistics = (Map<?, ?>) condition.get("seoulCommercialStatistics");
        var cutoff = Instant.parse((String) statistics.get("capturedAt"));
        var assumptions = new HashMap<>(MONTHLY_SALES.assumptions());
        assumptions.put("salesBasis", "분기 월평균 상권 매출을 점포 수로 나눈 추정치이며 개별 점포의 미래 매출을 보장하지 않습니다.");
        assumptions.put("runwayBasis", "매출이 0원일 때 잔여 자기자금으로 월 고정비를 충당할 수 있는 기간");
        assumptions.put("scoreRounding", "HALF_UP to 2 decimal places for display; grade thresholds use unrounded scores");
        return new Evaluation(sales, Set.of(OpeningCost.DEPOSIT, OpeningCost.KEY_MONEY), this::assess,
                RANKING, assumptions, VERSION, LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC), null, HALF_UP);
    }

    // Keep ratios as fractions until display so threshold tests and ties are exact.
    private record Fraction(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal display() {
            return numerator.divide(denominator, 2, HALF_UP);
        }

        boolean atLeast(BigDecimal value) {
            return numerator.compareTo(denominator.multiply(value)) >= 0;
        }

        Fraction plus(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
    }

    private static Fraction points(BigDecimal numerator, BigDecimal denominator, BigDecimal maximum) {
        return new Fraction(numerator.max(ZERO).min(denominator).multiply(maximum), denominator);
    }

    Scored assess(Financials f) {
        var cash = f.operatingCash();
        var fixed = f.fixedCost();
        var funding = fixed.signum() == 0 ? new Fraction(cash.signum() >= 0 ? FORTY : ZERO, ONE)
                : points(cash, fixed.multiply(SIX_MONTHS), FORTY);

        // profit / contribution before fixed costs equals (sales - exact break-even) / sales.
        var contribution = f.sales().subtract(f.variableCost());
        Fraction margin;
        if (f.sales().signum() == 0 || "UNREACHABLE".equals(f.breakEvenStatus())) {
            margin = new Fraction(ZERO, ONE);
        } else if (fixed.signum() == 0) {
            margin = new Fraction(ONE, ONE);
        } else {
            margin = new Fraction(f.profit(), contribution);
        }
        var safety = points(margin.numerator, margin.denominator.multiply(FULL_MARGIN), FORTY);
        var rent = f.sales().signum() == 0 ? new Fraction(ZERO, ONE)
                : points(HIGH_RENT.multiply(f.sales()).subtract(f.occupancyCost()),
                        HIGH_RENT.subtract(LOW_RENT).multiply(f.sales()), TWENTY);
        var total = funding.plus(safety).plus(rent);

        var restrictions = new ArrayList<String>();
        if (cash.signum() < 0) restrictions.add("초기 필요자금이 가용 자기자금을 초과함");
        if (f.profit().signum() <= 0) restrictions.add("예상 월 영업이익이 0원 이하임");
        GradeBand grade = C;
        if (!restrictions.isEmpty()) {
            grade = new GradeBand(ZERO, "C", String.join(", ", restrictions));
        } else if (total.atLeast(A.minimumScore()) && cash.compareTo(fixed.multiply(BigDecimal.valueOf(3))) >= 0
                && margin.atLeast(new BigDecimal("0.20"))) {
            grade = A;
        } else if (total.atLeast(B.minimumScore()) && cash.compareTo(fixed) >= 0 && margin.atLeast(new BigDecimal("0.05"))) {
            grade = B;
        }
        var details = Map.<String, Object>of("fundingScore", funding.display(), "breakEvenMarginScore", safety.display(),
                "rentBurdenScore", rent.display(), "gradeRestrictions", List.copyOf(restrictions));
        return new Scored(f, total.display(), grade, details);
    }

    static final Comparator<Scored> RANKING = Comparator.comparing((Scored s) -> s.band().grade())
            .thenComparing(Scored::score, Comparator.reverseOrder())
            .thenComparing((left, right) -> compareRunway(right.financials(), left.financials()))
            .thenComparing(s -> s.financials().profit(), Comparator.reverseOrder())
            .thenComparingLong(s -> s.financials().id());

    private static int compareRunway(Financials left, Financials right) {
        // With no fixed cost, funded properties have unlimited runway; an unfunded opening has none.
        boolean leftUnlimited = left.fixedCost().signum() == 0 && left.operatingCash().signum() >= 0;
        boolean rightUnlimited = right.fixedCost().signum() == 0 && right.operatingCash().signum() >= 0;
        if (leftUnlimited || rightUnlimited) return Boolean.compare(leftUnlimited, rightUnlimited);
        var leftMonths = runway(left);
        var rightMonths = runway(right);
        return leftMonths.numerator.multiply(rightMonths.denominator)
                .compareTo(rightMonths.numerator.multiply(leftMonths.denominator));
    }

    private static Fraction runway(Financials f) {
        return f.fixedCost().signum() == 0 ? new Fraction(ZERO, ONE) : new Fraction(f.operatingCash(), f.fixedCost());
    }
}
