package com.irion.irion_backend.domain.aiConsultation.comparison.client;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation.Report;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import java.math.BigDecimal;
import java.util.*;

public interface ComparisonConsultationAiClient {
    record Input(List<PropertySnapshot> properties, boolean differentConditions, boolean differentCalculationBasis) {
        public static Input from(List<PropertySnapshot> properties) {
            var conditions = properties.stream().map(s -> PropertySnapshot.comparisonCondition(s.conditionSnapshot())).toList();
            // The historical database ID is present in both generations. Compare codes when known.
            var common = new HashMap<>(conditions.get(0));
            common.remove("industryCode");
            boolean different = conditions.stream().anyMatch(values -> !values.entrySet().containsAll(common.entrySet()))
                    || conditions.stream().map(values -> values.get("industryCode")).filter(Objects::nonNull)
                            .distinct().count() > 1;
            boolean bases = properties.stream().map(s -> List.of(financialVersion(s), AiProperty.basis(s)))
                    .distinct().count() > 1;
            return new Input(List.copyOf(properties), different, bases);
        }

        private static String financialVersion(PropertySnapshot s) {
            if (s.assumptionsSnapshot().get("stressTest") instanceof Map<?,?> stress
                    && stress.get("assessmentPolicy") instanceof String policy
                    && stress.get("version") instanceof String version
                    && s.calculationVersion().equals(policy + "+" + version)) {
                return policy;
            }
            return s.calculationVersion();
        }
    }
    record AiProperty(Long propertyAnalysisId, String name, String address, String addressDetail, BigDecimal areaM2,
            Long deposit, Long monthlyRent, Long keyMoney, Long monthlyManagementFee,
            java.time.LocalDateTime analyzedAt, java.time.LocalDateTime dataCutoffAt, String calculationVersion,
            Map<String,Object> condition, Map<String,Object> assumptions, PropertySnapshot.FinancialResult financialResult) {
        static AiProperty from(PropertySnapshot s) {
            return new AiProperty(s.propertyAnalysisId(), s.name(), s.address(), s.addressDetail(), s.areaM2(), s.deposit(),
                    s.monthlyRent(), s.keyMoney(), s.monthlyManagementFee(), s.analyzedAt(), s.dataCutoffAt(),
                    s.calculationVersion(), PropertySnapshot.comparisonCondition(s.conditionSnapshot()), audit(s), s.financialResult());
        }

        private static Map<String,Object> basis(PropertySnapshot s) {
            var audit = new LinkedHashMap<String,Object>();
            var source = s.assumptionsSnapshot();
            strings(source, audit, "salesBasis", "runwayBasis", "scoreRounding");
            if (source.get("monthlySalesEstimation") instanceof Map<?,?> estimation) {
                var method = new LinkedHashMap<String,Object>();
                strings(estimation, method, "sourcePath", "formula", "periodInterpretation", "divisionRounding");
                if (estimation.get("divisionScale") instanceof Integer scale && scale >= 0) method.put("divisionScale", scale);
                if (!method.isEmpty()) audit.put("monthlySalesEstimation", method);
            }
            if (source.get("financialCalculation") instanceof Map<?,?> calculation) {
                strings(calculation, audit, "capitalBasis", "wonRounding");
                stringList(calculation, audit, "openingCosts");
            }
            return audit;
        }

        private static Map<String,Object> audit(PropertySnapshot s) {
            var audit = basis(s);
            var source = s.assumptionsSnapshot();
            if (source.get("financialCalculation") instanceof Map<?,?> calculation) {
                for (String key : List.of("approvedMonthlySalesWonByProperty", "monthlyVariableCostWonByProperty")) {
                    if (calculation.get(key) instanceof Map<?,?> byProperty && byProperty.containsKey(s.propertyId().toString()))
                        number(byProperty.get(s.propertyId().toString()), audit, key);
                }
            }
            if (source.get("stabilityByProperty") instanceof Map<?,?> scores && scores.get(s.propertyId().toString()) instanceof Map<?,?> score) {
                for (String key : List.of("fundingScore", "breakEvenMarginScore", "rentBurdenScore"))
                    number(score.get(key), audit, key);
                stringList(score, audit, "gradeRestrictions");
            }
            return audit;
        }

        private static void strings(Map<?,?> from, Map<String,Object> to, String... keys) {
            for (String key : keys)
                if (from.get(key) instanceof String value && !value.isBlank()) to.put(key, value);
        }

        private static void number(Object value, Map<String,Object> to, String key) {
            if (!(value instanceof Number || value instanceof String)) return;
            try {
                to.put(key, new BigDecimal(value.toString()));
            } catch (NumberFormatException invalidOptionalValue) {
                // Historical audit entries are optional. The validated financial result remains authoritative.
            }
        }

        private static void stringList(Map<?,?> from, Map<String,Object> to, String key) {
            if (from.get(key) instanceof List<?> values
                    && values.stream().allMatch(v -> v instanceof String text && !text.isBlank()))
                to.put(key, List.copyOf(values));
        }
    }

    Report generate(Input input);
}
