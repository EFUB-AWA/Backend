package com.irion.irion_backend.domain.costbenchmark.dto;

import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmark;
import java.math.BigDecimal;
import java.util.List;

public record CostBenchmarkResponse(
        Long costBenchmarkId,
        Long industryId,
        Long monthlyLaborCost,
        BigDecimal costOfSalesRate,
        Long monthlyOtherOperatingCost,
        Long monthlyTotalOperatingCost,
        String benchmarkType,
        List<Long> externalDataSnapshotIds
) {
    public static CostBenchmarkResponse of(
            Long industryId,
            CostBenchmark benchmark,
            String benchmarkType,
            List<Long> snapshotIds
    ) {
        return new CostBenchmarkResponse(
                benchmark.getId(),
                industryId,
                benchmark.getMonthlyLaborCost(),
                benchmark.getCostOfSalesRate(),
                benchmark.getMonthlyOtherOperatingCost(),
                Math.addExact(
                        benchmark.getMonthlyLaborCost(),
                        benchmark.getMonthlyOtherOperatingCost()
                ),
                benchmarkType,
                List.copyOf(snapshotIds)
        );
    }
}

