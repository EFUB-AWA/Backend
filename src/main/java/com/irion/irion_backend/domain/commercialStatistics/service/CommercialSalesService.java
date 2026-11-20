package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.*;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SALES_QUARTER_NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.salesField;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableDecimal;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readNullableLong;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Service
@RequiredArgsConstructor
public class CommercialSalesService {

    private static final String SERVICE_NAME = "VwsmTrdarSelngQq";
    private static final int PAGE_SIZE = 1000;
    private static final Duration MINIMUM_SALES_CACHE_TTL = Duration.ofDays(2);

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final CommercialStatisticsCacheRepository cacheRepository;
    private final Object salesLoadMonitor = new Object();

    public Optional<EstimatedSalesStatistics> findStatistics(
            String quarterCode,
            String commercialAreaCode,
            String industryCode
    ) {
        if (quarterCode == null || !quarterCode.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("기준 분기 코드는 연도 4자리와 분기 1자리로 입력해야 합니다.");
        }
        validateLookupCodes(commercialAreaCode, industryCode);

        CommercialStatisticsCacheRepository.SalesLookup cached = cacheRepository.findSales(quarterCode, commercialAreaCode, industryCode);
        if (cached.loaded()) {
            return cached.statistics();
        }

        synchronized (salesLoadMonitor) {
            cached = cacheRepository.findSales(quarterCode, commercialAreaCode, industryCode);
            if (cached.loaded()) {
                return cached.statistics();
            }

            Map<String, EstimatedSalesStatistics> statisticsByField = collectSalesQuarter(quarterCode);
            cacheRepository.saveSalesQuarter(quarterCode, statisticsByField);

            return Optional.ofNullable(statisticsByField.get(salesField(commercialAreaCode, industryCode)));
        }
    }

    public Optional<EstimatedSalesStatistics> findLatestStatistics(String commercialAreaCode, String industryCode) {
        validateLookupCodes(commercialAreaCode, industryCode);

        String quarterCode = cacheRepository.findLatestSalesQuarter()
                .orElseThrow(() -> new BusinessException(SALES_QUARTER_NOT_READY));
        var cached = cacheRepository.findPublishedSales(quarterCode, commercialAreaCode, industryCode);
        if (!cached.loaded()) {
            throw new BusinessException(SALES_QUARTER_NOT_READY);
        }
        return cached.statistics();
    }

    boolean isSalesQuarterPublished(String quarterCode) {
        var page = seoulOpenApiClient.fetchPage(SERVICE_NAME, 1, 1, quarterCode);
        if (page.rows().isEmpty()) {
            return false;
        }

        validateRowQuarter(page.rows().get(0), quarterCode);
        return true;
    }

    void prepareSalesQuarter(String quarterCode) {
        synchronized (salesLoadMonitor) {
            if (cacheRepository.hasSalesQuarterWithTtl(quarterCode, MINIMUM_SALES_CACHE_TTL)) {
                return;
            }

            Map<String, EstimatedSalesStatistics> statisticsByField = collectSalesQuarter(quarterCode);
            if (statisticsByField.isEmpty()) {
                throw new BusinessException(SALES_QUARTER_NOT_READY);
            }
            cacheRepository.saveSalesQuarter(quarterCode, statisticsByField);
        }
    }

    private void validateLookupCodes(String commercialAreaCode, String industryCode) {
        if (commercialAreaCode == null || commercialAreaCode.isBlank()
                || industryCode == null || industryCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드와 업종 코드는 필수입니다.");
        }
    }

    private Map<String, EstimatedSalesStatistics> collectSalesQuarter(String quarterCode) {
        Map<String, EstimatedSalesStatistics> statisticsByField = new HashMap<>();

        seoulOpenApiClient.forEachPage(SERVICE_NAME, PAGE_SIZE, rows -> {
            for (JsonNode row : rows) {
                validateRowQuarter(row, quarterCode);

                String areaCode = readRequiredCode(row, "TRDAR_CD");
                String industryCode = readRequiredCode(row, "SVC_INDUTY_CD");

                EstimatedSalesStatistics statistics = new EstimatedSalesStatistics(
                        quarterCode,
                        areaCode,
                        industryCode,
                        readNullableDecimal(row, "THSMON_SELNG_AMT"),
                        readNullableLong(row, "THSMON_SELNG_CO"),
                        readNullableDecimal(row, "MDWK_SELNG_AMT"),
                        readNullableDecimal(row, "WKEND_SELNG_AMT")
                );

                String field = salesField(areaCode, industryCode);
                if (statisticsByField.putIfAbsent(field, statistics) != null) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }
            }
        }, quarterCode);

        return statisticsByField;
    }

    private void validateRowQuarter(JsonNode row, String quarterCode) {
        if (!quarterCode.equals(readRequiredCode(row, "STDR_YYQU_CD"))) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
    }
}
