package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.SalesLookup;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.SnapshotLookup;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialSalesService;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialStatisticsService;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialStoreService;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisCommercialStatisticsTest {
    private static final String Q = "20262", AREA = "3110621", INDUSTRY = "CS100004";
    private final CommercialStatisticsCacheRepository cache = mock(CommercialStatisticsCacheRepository.class);
    private final SeoulOpenApiClient api = mock(SeoulOpenApiClient.class);
    private final IndustryRepository industries = mock(IndustryRepository.class);
    private final AnalysisCommercialStatistics collector = new AnalysisCommercialStatistics(
            new CommercialSalesService(api, cache), new CommercialStatisticsService(cache, new CommercialStoreService(api)), industries);

    private Map<String, Object> collect(Map<String, Object> condition) {
        var property = mock(PropertySnapshotV1.class);
        when(property.commercialAreaCode()).thenReturn(AREA);
        return collector.collect(condition, List.of(property));
    }

    private void sales(BigDecimal amount) {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of(Q));
        when(cache.findPublishedSales(Q, AREA, INDUSTRY)).thenReturn(new SalesLookup(true, Optional.of(
                new EstimatedSalesStatistics(Q, AREA, INDUSTRY, amount, null, null, null))));
    }

    private void stores(String quarter, Long count) {
        when(cache.findStoreStatistics(Q, AREA, INDUSTRY)).thenReturn(new SnapshotLookup<>(true, Optional.of(
                new StoreStatistics(quarter, AREA, INDUSTRY, count, null, null, null, null, null, null))));
    }

    @Test
    void preservesRawFieldsAndDatasetNames() {
        sales(BigDecimal.valueOf(111952232));
        stores(Q, 3L);
        var byArea = (Map<?, ?>) collect(Map.of("industryCode", INDUSTRY)).get("byCommercialAreaCode");
        assertEquals(Map.of("commercialAreaCode", AREA, "quarterCode", Q, "industryCode", INDUSTRY,
                "reportedSalesAmountWon", "111952232", "similarIndustryStoreCount", 3L,
                "salesSource", "VwsmTrdarSelngQq", "salesField", "THSMON_SELNG_AMT",
                "storesSource", "VwsmTrdarStorQq", "storesField", "SIMILR_INDUTY_STOR_CO"), byArea.get(AREA));
        verifyNoInteractions(api);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quarter mismatch", "zero stores", "null stores", "negative stores", "sales quarter mismatch", "missing amount", "missing sales", "sales cache miss"})
    void rejectsUnavailableOrInconsistentObservations(String scenario) {
        sales(BigDecimal.TEN);
        stores(Q, 3L);
        switch (scenario) {
            case "quarter mismatch" -> stores("20261", 3L);
            case "zero stores" -> stores(Q, 0L);
            case "null stores" -> stores(Q, null);
            case "negative stores" -> stores(Q, -1L);
            case "sales quarter mismatch" -> when(cache.findPublishedSales(Q, AREA, INDUSTRY))
                    .thenReturn(new SalesLookup(true, Optional.of(new EstimatedSalesStatistics(
                            "20261", AREA, INDUSTRY, BigDecimal.TEN, null, null, null))));
            case "missing amount" -> sales(null);
            case "missing sales" -> when(cache.findPublishedSales(Q, AREA, INDUSTRY))
                    .thenReturn(new SalesLookup(true, Optional.empty()));
            case "sales cache miss" -> when(cache.findPublishedSales(Q, AREA, INDUSTRY))
                    .thenReturn(new SalesLookup(false, Optional.empty()));
        }
        assertThrows(RuntimeException.class, () -> collect(Map.of("industryCode", INDUSTRY)));
        verifyNoInteractions(api);
    }

    @Test
    void pinsQuarterAcrossDifferentAreasAndReusesObservationsForSharedAreas() {
        sales(BigDecimal.TEN);
        stores(Q, 3L);
        String otherArea = "3110622";
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of(Q), Optional.of("20263"));
        when(cache.findPublishedSales(Q, otherArea, INDUSTRY)).thenReturn(new SalesLookup(true, Optional.of(
                new EstimatedSalesStatistics(Q, otherArea, INDUSTRY, BigDecimal.ONE, null, null, null))));
        when(cache.findStoreStatistics(Q, otherArea, INDUSTRY)).thenReturn(new SnapshotLookup<>(true, Optional.of(
                new StoreStatistics(Q, otherArea, INDUSTRY, 1L, null, null, null, null, null, null))));
        var a = mock(PropertySnapshotV1.class);
        var b = mock(PropertySnapshotV1.class);
        var c = mock(PropertySnapshotV1.class);
        when(a.commercialAreaCode()).thenReturn(AREA);
        when(b.commercialAreaCode()).thenReturn(otherArea);
        when(c.commercialAreaCode()).thenReturn(AREA);

        var snapshot = collector.collect(Map.of("industryCode", INDUSTRY), List.of(a, b, c));
        var areas = (Map<?, ?>) snapshot.get("byCommercialAreaCode");
        assertEquals(2, areas.size());
        assertEquals(Q, ((Map<?, ?>) areas.get(otherArea)).get("quarterCode"));
        verify(cache).findLatestSalesQuarter();
        verify(cache).findPublishedSales(Q, AREA, INDUSTRY);
        verify(cache).findStoreStatistics(Q, AREA, INDUSTRY);
        verifyNoInteractions(api);
    }

    @Test
    void storeCacheMissPropagatesUpstreamFailure() {
        sales(BigDecimal.TEN);
        when(cache.findStoreStatistics(Q, AREA, INDUSTRY)).thenReturn(new SnapshotLookup<>(false, Optional.empty()));
        var failure = new IllegalStateException("upstream unavailable");
        doThrow(failure).when(api).forEachPage(eq("VwsmTrdarStorQq"), anyInt(), any(), eq(Q), eq(AREA));
        assertSame(failure, assertThrows(RuntimeException.class, () -> collect(Map.of("industryCode", INDUSTRY))));
    }

    @Test
    void unmappedLegacyIndustryFailsBeforeStatisticsAccess() {
        assertThrows(IllegalStateException.class, () -> collect(Map.of("industryId", 27)));
        verify(industries).findById(27L);
        verifyNoInteractions(cache, api);
    }
}
