package com.irion.irion_backend.domain.analysis.dto;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record AnalysisDetailResponse(
        Long analysisRunId, String title, AnalysisStatus status, LocalDateTime completedAt,
        LocalDateTime dataCutoffAt, Integer horizonMonths, StartupConditionSummary startupCondition,
        List<PropertySummary> properties
) {
    public record StartupConditionSummary(Long industryId, String preferredRegionCode, Long totalCapital,
                                          Long availableCapital, Long loanAmount, BigDecimal annualInterestRate) {}

    public record PropertySummary(Long propertyAnalysisId, Long candidatePropertyId, String propertyName,
                                  String address, Long expectedMonthlySales, Long monthlyOperatingProfit,
                                  Long breakEvenSales, Long initialRequiredCash, BigDecimal safetyScore,
                                  String safetyGrade, Integer recommendationRank, String recommendationReason) {}

    public static AnalysisDetailResponse from(AnalysisRun run, List<PropertyAnalysis> rows) {
        var ids = run.getCandidatePropertyIds();
        var snapshots = indexedSnapshots(run, ids);
        var expected = new HashSet<>(ids);
        var summaries = new ArrayList<PropertySummary>();
        for (var row : rows) {
            Long id = row.getCandidateProperty().getId();
            if (!expected.remove(id)) {
                throw new IllegalStateException("Unexpected or duplicate candidate result");
            }
            summaries.add(propertySummary(row, id, snapshots.get(id)));
        }
        if (!expected.isEmpty()) throw new IllegalStateException("Missing candidate results");

        summaries.sort(Comparator.comparing(PropertySummary::recommendationRank,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PropertySummary::propertyAnalysisId));
        var condition = run.getConditionSnapshot();
        var startup = new StartupConditionSummary(number(condition, "industryId"), text(condition.get("preferredRegionCode")),
                amount(condition, "totalCapital"), amount(condition, "availableCapital"), amount(condition, "loanAmount"), null);
        return new AnalysisDetailResponse(run.getId(), run.getTitle(), run.getStatus(), run.getCompletedAt(),
                run.getDataCutoffAt(), run.getHorizonMonths(), startup, List.copyOf(summaries));
    }

    static Map<Long, PropertySnapshotV1> indexedSnapshots(AnalysisRun run, List<Long> ids) {
        var snapshots = new HashMap<Long, PropertySnapshotV1>();
        for (var snapshot : run.getPropertySnapshots()) {
            Long candidateId = Objects.requireNonNull(snapshot.candidatePropertyId(), "Missing snapshot candidate ID");
            if (snapshots.put(candidateId, snapshot) != null) {
                throw new IllegalStateException("Duplicate property snapshot");
            }
        }
        var expected = new HashSet<>(ids);
        if (ids.isEmpty() || expected.size() != ids.size() || !expected.equals(snapshots.keySet())) {
            throw new IllegalStateException("Property snapshots do not match the requested candidates");
        }
        return snapshots;
    }

    static Map<String, Object> resultSnapshot(PropertyAnalysis row, Long id) {
        Map<String, Object> stored = row.getPropertySnapshot();
        if (stored == null) stored = Map.of();
        if (stored.containsKey("candidatePropertyId") && number(stored, "candidatePropertyId") != id.longValue()) {
            throw new IllegalStateException("Candidate result and snapshot IDs differ");
        }
        return stored;
    }

    private static PropertySummary propertySummary(PropertyAnalysis row, Long id, PropertySnapshotV1 snapshot) {
        var stored = resultSnapshot(row, id);
        var breakEven = row.getBreakEvenSales();
        if (breakEven == null && !CalculatedPropertyResult.UNREACHABLE.equals(row.getBreakEvenStatus())) {
            throw new IllegalStateException("Missing break-even sales");
        }
        return new PropertySummary(Objects.requireNonNull(row.getId()), id,
                snapshotText(stored.get("name"), snapshot.name()), snapshotText(stored.get("address"), snapshot.address()),
                Objects.requireNonNull(row.getExpectedMonthlySales()), Objects.requireNonNull(row.getMonthlyOperatingProfit()),
                breakEven, Objects.requireNonNull(row.getInitialRequiredCash()), Objects.requireNonNull(row.getSafetyScore()),
                grade(row.getSafetyGrade()), row.getRecommendationRank(), row.getRecommendationReason());
    }

    static String snapshotText(Object resultValue, String runValue) {
        if (resultValue == null) return text(runValue);
        String value = text(resultValue);
        if (runValue != null && !value.equals(runValue)) throw new IllegalStateException("Conflicting property snapshots");
        return value;
    }

    private static String text(Object value) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalStateException("Invalid snapshot text");
    }

    private static long number(Map<String, Object> snapshot, String key) {
        if (!(snapshot.get(key) instanceof Number n)) throw new IllegalStateException("Invalid snapshot number: " + key);
        if (n instanceof Double || n instanceof Float) {
            // A binary floating-point value cannot establish the original JSON integer beyond its safe range.
            double limit = n instanceof Float ? (1L << 24) : (1L << 53);
            double value = n.doubleValue();
            if (!Double.isFinite(value) || Math.abs(value) >= limit || value != Math.rint(value)) {
                throw new IllegalStateException("Inexact snapshot number: " + key);
            }
            return (long) value;
        }
        return new BigDecimal(n.toString()).longValueExact();
    }

    private static long amount(Map<String, Object> snapshot, String key) {
        long value = number(snapshot, key);
        if (value < 0) throw new IllegalStateException("Negative snapshot amount: " + key);
        return value;
    }

    static String grade(String value) {
        return switch (Objects.requireNonNull(value, "Missing safety grade").strip().toUpperCase(Locale.ROOT)) {
            case "A", "STABLE", "안정" -> "A";
            case "B", "CAUTION", "주의" -> "B";
            case "C", "WARNING", "경고" -> "C";
            default -> throw new IllegalStateException("Unsupported safety grade");
        };
    }
}
