package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Supplies approved per-store monthly sales and assessment rules for frozen inputs. */
public interface CalculationPolicy {
    Evaluation evaluate(Map<String, Object> condition, List<PropertySnapshotV1> properties);

    enum OpeningCost { DEPOSIT, KEY_MONEY }

    record ScoreComponent(BigDecimal weightPercent, Function<Financials, BigDecimal> points) {
        public ScoreComponent {
            if (weightPercent.signum() <= 0) throw new IllegalArgumentException("Expected positive score weight");
            Objects.requireNonNull(points);
        }
    }

    record GradeBand(BigDecimal minimumScore, String grade, String reason) {
        public GradeBand {
            if (minimumScore.signum() < 0 || minimumScore.compareTo(BigDecimal.valueOf(100)) > 0
                    || grade.isBlank() || reason.isBlank()) throw new IllegalArgumentException("Invalid grade band");
        }
    }

    /** Grade thresholds are inclusive. The ranking comparator must resolve ties between distinct properties. */
    record Evaluation(Map<Long, BigDecimal> monthlySales, Set<OpeningCost> openingCosts,
                      List<ScoreComponent> components, List<GradeBand> grades, Comparator<Scored> ranking,
                      Map<String, Object> assumptions, String version, LocalDateTime dataCutoffAt,
                      int horizonMonths, RoundingMode wonRounding) {
        public Evaluation {
            monthlySales = Map.copyOf(monthlySales);
            openingCosts = Set.copyOf(openingCosts);
            components = List.copyOf(components);
            grades = grades.stream().sorted(Comparator.comparing(GradeBand::minimumScore).reversed()).toList();
            assumptions = Map.copyOf(assumptions);
            Objects.requireNonNull(ranking);
            Objects.requireNonNull(dataCutoffAt);
            Objects.requireNonNull(wonRounding);
            if (version.isBlank() || horizonMonths <= 0 || grades.isEmpty()
                    || grades.get(grades.size() - 1).minimumScore().signum() != 0
                    || components.stream().map(ScoreComponent::weightPercent).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new IllegalArgumentException("Incomplete calculation policy");
            }
            for (int i = 1; i < grades.size(); i++) {
                if (grades.get(i - 1).minimumScore().compareTo(grades.get(i).minimumScore()) == 0) {
                    throw new IllegalArgumentException("Duplicate grade threshold");
                }
            }
        }
    }

    record Financials(long id, BigDecimal sales, BigDecimal fixedCost, BigDecimal variableCost,
                      BigDecimal ratePercent, BigDecimal profit, BigDecimal breakEvenSales, String breakEvenStatus,
                      BigDecimal requiredCash, BigDecimal operatingCash) {}

    record Scored(Financials financials, BigDecimal score, GradeBand band) {}
}
