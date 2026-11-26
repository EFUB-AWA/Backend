package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.STATISTICS_QUARTER_NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.CONSUMPTION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.FLOATING_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.RESIDENT_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.STORES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.WORKER_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsQuarters.SEOUL;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsQuarters.findLatestPublishedQuarter;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsRow.readRequiredCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialStatisticsRefreshService {

    private static final int PAGE_SIZE = 1000;
    private static final Duration READY_TTL_MARGIN = Duration.ofDays(2);
    // 분기 필터가 없는 데이터는 14일 TTL의 절반이 남을 때 다시 전체 순회합니다.
    private static final Duration UNFILTERED_TTL_MARGIN = Duration.ofDays(7);

    private final SeoulOpenApiClient client;
    private final SeoulOpenApiProperties properties;
    private final CommercialStatisticsCacheRepository cache;
    private final CommercialFloatingPopulationService floatingPopulationService;
    private final CommercialResidentPopulationService residentPopulationService;
    private final CommercialWorkerPopulationService workerPopulationService;
    private final CommercialConsumptionService consumptionService;

    public void refreshStatistics() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return;
        }
        refreshStatistics(LocalDate.now(SEOUL));
    }

    void refreshStatistics(LocalDate today) {
        refresh(STORES, () -> publishQuarter(STORES, requireLatestQuarter(STORES, today)));
        refresh(FLOATING_POPULATION, () -> prepareQuarter(FLOATING_POPULATION,
                requireLatestQuarter(FLOATING_POPULATION, today), floatingPopulationService::toStatistics));
        refresh(RESIDENT_POPULATION, () -> refreshUnfiltered(RESIDENT_POPULATION, residentPopulationService::toStatistics));
        refresh(WORKER_POPULATION, () -> refreshUnfiltered(WORKER_POPULATION, workerPopulationService::toStatistics));
        refresh(CONSUMPTION, () -> refreshConsumption(today));
    }

    private void refresh(StatisticsDataset dataset, Runnable refresh) {
        try {
            refresh.run();
            cache.recordRefreshSuccess(dataset);
        } catch (BusinessException e) {
            cache.recordRefreshFailure(dataset);
            log.warn("상권 통계 갱신 실패. dataset={}, code={}", dataset, e.getErrorCode().getCode());
        }
    }

    private void refreshConsumption(LocalDate today) {
        // 갱신이 중단된 데이터셋은 처음에 공개 분기를 찾고 이후에는 그 분기를 유지합니다.
        var quarter = cache.findLatestQuarter(CONSUMPTION).or(() -> probeLatestQuarter(CONSUMPTION, today));
        if (quarter.isPresent()) {
            prepareQuarter(CONSUMPTION, quarter.get(), consumptionService::toStatistics);
        } else {
            refreshUnfiltered(CONSUMPTION, consumptionService::toStatistics);
        }
    }

    private String requireLatestQuarter(StatisticsDataset dataset, LocalDate today) {
        return probeLatestQuarter(dataset, today)
                .orElseThrow(() -> new BusinessException(STATISTICS_QUARTER_NOT_READY));
    }

    private Optional<String> probeLatestQuarter(StatisticsDataset dataset, LocalDate today) {
        return findLatestPublishedQuarter(today, candidate -> {
            var page = client.fetchPage(dataset.serviceName(), 1, 1, candidate);
            if (page.rows().isEmpty()) {
                return false;
            }
            verifyQuarter(page.rows().get(0), candidate);
            return true;
        });
    }

    private <T> void prepareQuarter(StatisticsDataset dataset, String quarter, RowMapper<T> mapper) {
        verifyNotOlder(dataset, quarter);
        if (!cache.hasQuarterWithTtl(dataset, quarter, READY_TTL_MARGIN)) {
            Map<String, T> statistics = new HashMap<>();
            client.forEachPage(dataset.serviceName(), PAGE_SIZE, rows -> {
                for (JsonNode row : rows) {
                    verifyQuarter(row, quarter);
                    if (!addRow(statistics, row, quarter, mapper)) {
                        throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                    }
                }
            }, quarter);
            saveQuarter(dataset, quarter, statistics);
        }
        cache.publishLatestQuarter(dataset, quarter);
    }

    private <T> void refreshUnfiltered(StatisticsDataset dataset, RowMapper<T> mapper) {
        Optional<String> current = cache.findLatestQuarter(dataset);
        if (current.isPresent() && cache.hasQuarterWithTtl(dataset, current.get(), UNFILTERED_TTL_MARGIN)) {
            return;
        }

        LatestQuarter<T> latest = new LatestQuarter<>();
        client.forEachPage(dataset.serviceName(), PAGE_SIZE, rows -> {
            for (JsonNode row : rows) {
                String quarter = readRequiredCode(row, "STDR_YYQU_CD");
                if (!quarter.matches("[0-9]{4}[1-4]")) {
                    throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
                }
                if (latest.quarterCode == null || quarter.compareTo(latest.quarterCode) > 0) {
                    latest.quarterCode = quarter;
                    latest.statistics.clear();
                    latest.duplicateArea = false;
                }
                if (quarter.equals(latest.quarterCode) && !addRow(latest.statistics, row, quarter, mapper)) {
                    latest.duplicateArea = true;
                }
            }
        });

        if (latest.quarterCode == null) {
            throw new BusinessException(STATISTICS_QUARTER_NOT_READY);
        }
        if (latest.duplicateArea) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
        verifyNotOlder(dataset, latest.quarterCode);
        saveQuarter(dataset, latest.quarterCode, latest.statistics);
        cache.publishLatestQuarter(dataset, latest.quarterCode);
    }

    private void publishQuarter(StatisticsDataset dataset, String quarter) {
        verifyNotOlder(dataset, quarter);
        cache.publishLatestQuarter(dataset, quarter);
    }

    private void verifyNotOlder(StatisticsDataset dataset, String quarter) {
        if (cache.findLatestQuarter(dataset).filter(current -> quarter.compareTo(current) < 0).isPresent()) {
            throw new BusinessException(STATISTICS_QUARTER_NOT_READY);
        }
    }

    private <T> void saveQuarter(StatisticsDataset dataset, String quarter, Map<String, T> statistics) {
        if (statistics.isEmpty()) {
            throw new BusinessException(STATISTICS_QUARTER_NOT_READY);
        }
        cache.saveQuarter(dataset, quarter, statistics);
    }

    private void verifyQuarter(JsonNode row, String expectedQuarter) {
        if (!expectedQuarter.equals(readRequiredCode(row, "STDR_YYQU_CD"))) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
    }

    private <T> boolean addRow(Map<String, T> statistics, JsonNode row, String quarter, RowMapper<T> mapper) {
        String areaCode = readRequiredCode(row, "TRDAR_CD");
        return statistics.putIfAbsent(areaCode, mapper.map(row, quarter, areaCode)) == null;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(JsonNode row, String quarterCode, String areaCode);
    }

    private static class LatestQuarter<T> {
        private String quarterCode;
        private final Map<String, T> statistics = new HashMap<>();
        private boolean duplicateArea;
    }
}
