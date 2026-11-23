package com.irion.irion_backend.domain.analysis.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisResultServiceTest {
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
