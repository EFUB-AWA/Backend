package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialSalesService;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialStatisticsService;
import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.SALES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.STORES;

@Service
@RequiredArgsConstructor
public class AnalysisCommercialStatistics {
    static final String SNAPSHOT_KEY = "seoulCommercialStatistics";
    private final CommercialSalesService salesService;
    private final CommercialStatisticsService statisticsService;
    private final IndustryRepository industries;

    Map<String, Object> collect(Map<String, Object> condition, List<PropertySnapshotV1> properties) {
        Object code = condition.get("industryCode");
        if (!condition.containsKey("industryCode") && condition.get("industryId") instanceof Number id) {
            // Older condition snapshots only stored the database ID.
            code = industries.findById(id.longValue()).map(Industry::getCode).orElse(null);
        }
        if (!(code instanceof String industry) || !industry.matches("CS[0-9]{6}")) {
            throw new IllegalStateException("No Seoul service industry code for the analysis");
        }

        var byArea = new HashMap<String, Map<String, Object>>();
        String quarter = salesService.latestPublishedQuarter();
        for (var property : properties) {
            String area = property.commercialAreaCode();
            if (byArea.containsKey(area)) {
                continue;
            }
            var sales = salesService.findPublishedStatistics(quarter, area, industry)
                    .orElseThrow(() -> new IllegalStateException("No published sales for " + area));
            if (!quarter.equals(sales.quarterCode())
                    || !area.equals(sales.commercialAreaCode()) || !industry.equals(sales.industryCode())
                    || sales.reportedSalesAmountWon() == null || sales.reportedSalesAmountWon().signum() < 0) {
                throw new IllegalStateException("Invalid sales observation for " + area);
            }
            var stores = statisticsService.findStoreStatistics(quarter, area, industry)
                    .orElseThrow(() -> new IllegalStateException("No store statistics for " + area));
            if (!quarter.equals(stores.quarterCode()) || !area.equals(stores.commercialAreaCode())
                    || !industry.equals(stores.industryCode()) || stores.totalStoreCount() == null
                    || stores.totalStoreCount() <= 0) {
                throw new IllegalStateException("Invalid store observation for " + area);
            }
            byArea.put(area, Map.of("commercialAreaCode", area, "industryCode", industry, "quarterCode", quarter,
                    "reportedSalesAmountWon", sales.reportedSalesAmountWon().toPlainString(),
                    "similarIndustryStoreCount", stores.totalStoreCount(),
                    "salesSource", SALES.serviceName(), "salesField", "THSMON_SELNG_AMT",
                    "storesSource", STORES.serviceName(), "storesField", "SIMILR_INDUTY_STOR_CO"));
        }
        return Map.of("byCommercialAreaCode", Map.copyOf(byArea), "schemaVersion", 1,
                "capturedAt", Instant.now().toString());
    }
}
