package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface FinancialCalculator {
    Result calculate(Map<String, Object> conditionSnapshot, List<PropertySnapshotV1> propertySnapshots);

    record Result(List<CalculatedPropertyResult> properties, Map<String, Object> assumptions,
                  String calculationVersion, LocalDateTime dataCutoffAt, Integer horizonMonths,
                  Map<Long, List<StressTestResult>> stressTests) {
        public Result(List<CalculatedPropertyResult> properties, Map<String, Object> assumptions,
                      String calculationVersion, LocalDateTime dataCutoffAt, Integer horizonMonths) {
            this(properties, assumptions, calculationVersion, dataCutoffAt, horizonMonths, Map.of());
        }
    }
}
