package com.irion.irion_backend.domain.analysis.service;

import org.junit.jupiter.api.Test;
import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.IntStream;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisResultServiceTest {
    @Test
    void rejectsMissingPropertiesScenariosAndMonthsEvenWhenDuplicatesMaskTheirCounts() {
        String version = "MVP_FINANCIAL_V1+STRESS_V1";
        var ids = List.of(1L);
        var months = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new StressTestResult.Month(i,0,0,0,0,0,0,0,0)).toList();
        var valid = List.of(0,10,20,30).stream().map(d -> new StressTestResult(d,null,"A",null,months)).toList();
        AnalysisResultService.requireCompleteStressTests(version, ids, Map.of(1L, valid));
        assertThrows(IllegalArgumentException.class, () -> AnalysisResultService.requireCompleteStressTests(version, ids, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> AnalysisResultService.requireCompleteStressTests(
                version, List.of(1L,2L), Map.of(1L, valid)));
        var duplicate = new ArrayList<>(valid);
        duplicate.set(3, valid.get(2));
        for (var invalid : List.of(valid.subList(0,3), duplicate)) {
            assertThrows(IllegalArgumentException.class, () -> AnalysisResultService.requireCompleteStressTests(version, ids, Map.of(1L, invalid)));
        }
        var repeatedMonth = new ArrayList<>(months);
        repeatedMonth.set(11, months.get(10));
        for (var invalid : List.of(months.subList(0,11), repeatedMonth)) {
            var scenarios = new ArrayList<>(valid);
            scenarios.set(3, new StressTestResult(30,null,"A",null,invalid));
            assertThrows(IllegalArgumentException.class, () -> AnalysisResultService.requireCompleteStressTests(version, ids, Map.of(1L, scenarios)));
        }
    }

    @Test
    void duplicatePropertyCannotIdentifyDistinctResultsEvenWhenCountsMatch() {
        var ids = List.of(1L, 1L);
        assertThrows(IllegalArgumentException.class, () -> AnalysisResultService.requireMatchingIds(ids, ids, ids));
    }

    @Test
    void snapshotIdsMustAgreeWithOriginalRequest() {
        var ids = List.of(1L, 2L);
        var reversed = List.of(2L, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> AnalysisResultService.requireMatchingIds(ids, reversed, reversed));
    }
}
