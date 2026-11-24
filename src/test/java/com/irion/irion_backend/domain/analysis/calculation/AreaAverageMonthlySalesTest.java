package com.irion.irion_backend.domain.analysis.calculation;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AreaAverageMonthlySalesTest {
    private final AreaAverageMonthlySales method = new AreaAverageMonthlySales(4, RoundingMode.HALF_UP);
    private final Map<String, Object> first = observation("A", "111952232", 3);
    private final Map<String, Object> second = observation("B", "0", 5);
    private final Map<String, Object> condition = new HashMap<>(Map.of("industryCode", "CS100004",
            "seoulCommercialStatistics", Map.of("schemaVersion", 1, "byCommercialAreaCode", Map.of("A", first, "B", second))));
    private final List<PropertySnapshotV1> properties = List.of(property(1, "A"), property(2, "B"), property(3, "A"));

    @Test
    void sharesEstimateWithinAreaAndAcceptsZeroSalesInAnotherArea() {
        var sales = method.estimate(condition, properties);
        assertEquals(new BigDecimal("37317410.6667"), sales.get(1L));
        assertEquals(sales.get(1L), sales.get(3L));
        assertEquals(0, sales.get(2L).signum());
        assertEquals(new BigDecimal("37317410"), new AreaAverageMonthlySales(0, RoundingMode.DOWN)
                .estimate(condition, properties).get(1L));
    }

    @Test
    void olderConditionCanUseIndustryInFrozenObservations() {
        condition.remove("industryCode");
        condition.put("industryId", 7);
        assertEquals(new BigDecimal("37317410.6667"), method.estimate(condition, properties).get(1L));
        second.put("industryCode", "CS100005");
        assertThrows(IllegalArgumentException.class, () -> method.estimate(condition, properties));
    }

    @ParameterizedTest
    @CsvSource({"reportedSalesAmountWon,", "reportedSalesAmountWon,-1", "similarIndustryStoreCount,",
            "similarIndustryStoreCount,0", "similarIndustryStoreCount,-1", "quarterCode,20261",
            "quarterCode,20265", "industryCode,CS100005", "commercialAreaCode,A"})
    void rejectsMissingInvalidOrInconsistentInputs(String field, String value) {
        second.put(field, value);
        assertThrows(IllegalArgumentException.class, () -> method.estimate(condition, properties));
    }

    private static Map<String, Object> observation(String area, String amount, int stores) {
        return new HashMap<>(Map.of("commercialAreaCode", area, "quarterCode", "20262", "industryCode", "CS100004",
                "reportedSalesAmountWon", amount, "similarIndustryStoreCount", stores));
    }

    private static PropertySnapshotV1 property(long id, String area) {
        return new PropertySnapshotV1(1, id, "property", "address", null, 0d, 0d, BigDecimal.ONE,
                0L, 0L, 0L, 0L, null, MappingStatus.MAPPED, area);
    }
}
