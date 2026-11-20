package com.irion.irion_backend.domain.commercialStatistics.dto;

import java.math.BigDecimal;

/** 후보 매물 분석에 사용하는 상권 통계. 각 지표의 기준 분기는 개별 결과에 담습니다. */
public record CommercialStatisticsResponse(
        String commercialAreaCode,
        String industryCode,
        DatasetResult<EstimatedSalesStatistics> estimatedSales,
        DatasetResult<StoreStatistics> stores,
        DatasetResult<FloatingPopulationStatistics> floatingPopulation,
        DatasetResult<ResidentPopulationStatistics> residentPopulation,
        DatasetResult<WorkerPopulationStatistics> workerPopulation,
        DatasetResult<ConsumptionStatistics> historicalConsumption,
        BigDecimal estimatedSalesPerStoreWon
) {
    public record DatasetResult<T>(String quarterCode, DatasetStatus status, boolean stale, T data) {}

    public enum DatasetStatus {
        AVAILABLE,
        NO_DATA,
        NOT_READY,
        UNAVAILABLE
    }
}
