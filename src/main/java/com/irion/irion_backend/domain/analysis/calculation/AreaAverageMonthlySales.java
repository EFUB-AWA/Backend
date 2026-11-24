package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Candidate MVP method for policy evaluation against frozen Seoul observations. */
public record AreaAverageMonthlySales(int divisionScale, RoundingMode divisionRounding) {
    public AreaAverageMonthlySales {
        if (divisionScale < 0) throw new IllegalArgumentException("Negative monthly sales scale");
        Objects.requireNonNull(divisionRounding);
    }

    public Map<Long, BigDecimal> estimate(Map<String, Object> condition, List<PropertySnapshotV1> properties) {
        if (!(condition.get("seoulCommercialStatistics") instanceof Map<?, ?> statistics)
                || !Integer.valueOf(1).equals(statistics.get("schemaVersion"))
                || !(statistics.get("byCommercialAreaCode") instanceof Map<?, ?> observations)) {
            throw new IllegalArgumentException("Expected frozen Seoul statistics version 1");
        }
        var sales = new HashMap<Long, BigDecimal>();
        var byArea = new HashMap<String, BigDecimal>();
        String quarter = null;
        Object industry = condition.get("industryCode");
        if (condition.containsKey("industryCode") && industry == null) {
            throw new IllegalArgumentException("Missing condition industry code");
        }
        for (var property : properties) {
            String area = property.commercialAreaCode();
            if (area == null || area.isBlank()) throw new IllegalArgumentException("Missing commercial area code");
            if (!byArea.containsKey(area)) {
                if (!(observations.get(area) instanceof Map<?, ?> row) || !area.equals(row.get("commercialAreaCode"))) {
                    throw new IllegalArgumentException("Missing or mismatched area observation for " + area);
                }
                if (!(row.get("quarterCode") instanceof String rowQuarter) || !rowQuarter.matches("[0-9]{4}[1-4]")
                        || (quarter != null && !quarter.equals(rowQuarter))) {
                    throw new IllegalArgumentException("Invalid or inconsistent quarter for " + area);
                }
                if (!(row.get("industryCode") instanceof String rowIndustry) || !rowIndustry.matches("CS[0-9]{6}")
                        || (industry != null && !rowIndustry.equals(industry))) {
                    throw new IllegalArgumentException("Invalid or inconsistent industry for " + area);
                }
                quarter = rowQuarter;
                industry = rowIndustry;
                var amount = decimal(row.get("reportedSalesAmountWon"), "reportedSalesAmountWon");
                var stores = decimal(row.get("similarIndustryStoreCount"), "similarIndustryStoreCount");
                if (amount.signum() < 0 || stores.longValueExact() <= 0) {
                    throw new IllegalArgumentException("Invalid sales or store count for " + area);
                }
                byArea.put(area, amount.divide(stores, divisionScale, divisionRounding));
            }
            sales.put(property.candidatePropertyId(), byArea.get(area));
        }
        return Map.copyOf(sales);
    }

    public Map<String, Object> assumptions() {
        return Map.of("monthlySalesEstimation", Map.of(
                "sourcePath", "conditionSnapshot.seoulCommercialStatistics.byCommercialAreaCode",
                "formula", "reportedSalesAmountWon / similarIndustryStoreCount",
                "periodInterpretation", "MONTHLY_AVERAGE_FOR_REPORTED_QUARTER",
                "divisionScale", divisionScale, "divisionRounding", divisionRounding.name()));
    }

    private static BigDecimal decimal(Object value, String field) {
        if (value instanceof Number || value instanceof String) return new BigDecimal(value.toString());
        throw new IllegalArgumentException("Missing or invalid " + field);
    }
}
