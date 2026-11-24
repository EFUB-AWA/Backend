package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse;
import com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetResult;
import com.irion.irion_backend.domain.commercialStatistics.dto.ConsumptionStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.FloatingPopulationStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.ResidentPopulationStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.WorkerPopulationStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.DatasetState;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.SnapshotLookup;
import com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Function;

import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.AVAILABLE;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.NO_DATA;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.UNAVAILABLE;
import static com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.salesField;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.CONSUMPTION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.FLOATING_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.RESIDENT_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.SALES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.STORES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.WORKER_POPULATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialStatisticsService {

    private final CommercialStatisticsCacheRepository cache;
    private final CommercialStoreService storeService;
    private final Object storeLoadMonitor = new Object();

    public CommercialStatisticsResponse getStatistics(String commercialAreaCode, String industryCode) {
        if (commercialAreaCode == null || commercialAreaCode.isBlank()
                || industryCode == null || industryCode.isBlank()) {
            throw new IllegalArgumentException("상권 코드와 업종 코드는 필수입니다.");
        }

        var sales = readQuarter(SALES, salesField(commercialAreaCode, industryCode), EstimatedSalesStatistics.class);
        var stores = readDataset(STORES, quarter -> readStores(quarter, commercialAreaCode, industryCode));
        var floating = readQuarter(FLOATING_POPULATION, commercialAreaCode, FloatingPopulationStatistics.class);
        var residents = readQuarter(RESIDENT_POPULATION, commercialAreaCode, ResidentPopulationStatistics.class);
        var workers = readQuarter(WORKER_POPULATION, commercialAreaCode, WorkerPopulationStatistics.class);
        var consumption = readQuarter(CONSUMPTION, commercialAreaCode, ConsumptionStatistics.class);

        return new CommercialStatisticsResponse(commercialAreaCode, industryCode, sales, stores, floating,
                residents, workers, consumption, salesPerStore(sales, stores));
    }

    private <T> DatasetResult<T> readQuarter(StatisticsDataset dataset, String field, Class<T> type) {
        return readDataset(dataset, quarter -> cache.findQuarterStatistics(dataset, quarter, field, type));
    }

    private <T> DatasetResult<T> readDataset(StatisticsDataset dataset, Function<String, SnapshotLookup<T>> lookup) {
        String quarter = null;
        try {
            DatasetState state = cache.findDatasetState(dataset);
            quarter = state.quarterCode();
            if (quarter != null) {
                SnapshotLookup<T> cached = lookup.apply(quarter);
                if (cached.loaded()) {
                    return new DatasetResult<>(quarter, cached.statistics().isPresent() ? AVAILABLE : NO_DATA,
                            state.refreshFailed(), cached.statistics().orElse(null));
                }
            }
            return new DatasetResult<>(quarter, state.refreshFailed() ? UNAVAILABLE : NOT_READY, false, null);
        } catch (BusinessException e) {
            log.warn("상권 통계 조회 실패. dataset={}, code={}", dataset, e.getErrorCode().getCode());
            return new DatasetResult<>(quarter, UNAVAILABLE, false, null);
        }
    }

    public Optional<StoreStatistics> findStoreStatistics(String quarter, String areaCode, String industryCode) {
        return readStores(quarter, areaCode, industryCode).statistics();
    }

    private SnapshotLookup<StoreStatistics> readStores(String quarter, String areaCode, String industryCode) {
        var cached = cache.findStoreStatistics(quarter, areaCode, industryCode);
        if (cached.loaded()) {
            return cached;
        }

        synchronized (storeLoadMonitor) {
            cached = cache.findStoreStatistics(quarter, areaCode, industryCode);
            if (cached.loaded()) {
                return cached;
            }

            var statisticsByIndustry = storeService.collectAreaStatistics(quarter, areaCode);
            cache.saveStoreArea(quarter, areaCode, statisticsByIndustry);
            return new SnapshotLookup<>(true, Optional.ofNullable(statisticsByIndustry.get(industryCode)));
        }
    }

    private BigDecimal salesPerStore(DatasetResult<EstimatedSalesStatistics> sales, DatasetResult<StoreStatistics> stores) {
        if (sales.data() == null || stores.data() == null || !sales.quarterCode().equals(stores.quarterCode())) {
            return null;
        }
        BigDecimal amount = sales.data().reportedSalesAmountWon();
        Long count = stores.data().totalStoreCount();
        if (amount == null || count == null || count <= 0) {
            return null;
        }
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
