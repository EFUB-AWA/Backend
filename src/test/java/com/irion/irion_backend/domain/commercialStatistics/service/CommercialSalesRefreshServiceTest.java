package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.SalesLookup;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.COMMERCIAL_STATS_CACHE_FAILED;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SALES_QUARTER_NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CommercialSalesRefreshServiceTest {

    private static final String SERVICE = "VwsmTrdarSelngQq";
    private static final String AREA = "3130167";
    private static final String INDUSTRY = "CS300011";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final Duration MINIMUM_TTL = Duration.ofDays(2);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final CommercialStatisticsCacheRepository cache = mock(CommercialStatisticsCacheRepository.class);
    private final CommercialSalesService sales = new CommercialSalesService(client, cache);
    private final CommercialSalesRefreshService refresh = new CommercialSalesRefreshService(sales, cache,
            new SeoulOpenApiProperties("test-key", "http://localhost", Duration.ofSeconds(3), Duration.ofSeconds(10)));

    @Test
    void probesFromTheCurrentQuarterAndPublishesAfterCaching() {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of("20262"));
        when(client.fetchPage(SERVICE, 1, 1, "20264")).thenReturn(new SeoulOpenApiClient.Page(0, List.of()));
        givenPublishedQuarter("20263");
        givenQuarterCollection("20263");

        refresh.refreshSales(TODAY);

        var order = inOrder(client, cache);
        order.verify(client).fetchPage(SERVICE, 1, 1, "20264");
        order.verify(client).fetchPage(SERVICE, 1, 1, "20263");
        order.verify(cache).saveSalesQuarter("20263", Map.of(AREA + ":" + INDUSTRY, statistics("20263")));
        order.verify(cache).publishLatestSalesQuarter("20263");
        verify(client, never()).fetchPage(SERVICE, 1, 1, "20262");
    }

    @Test
    void skipsFullCollectionWhenTheCurrentCacheHasEnoughTimeLeft() {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of("20264"));
        when(cache.hasSalesQuarterWithTtl("20264", MINIMUM_TTL)).thenReturn(true);
        givenPublishedQuarter("20264");

        refresh.refreshSales(TODAY);

        verify(client).fetchPage(SERVICE, 1, 1, "20264");
        verifyNoMoreInteractions(client);
        verify(cache, never()).saveSalesQuarter(anyString(), any());
        verify(cache, never()).publishLatestSalesQuarter(anyString());
    }

    @Test
    void refreshesTheSameQuarterBeforeItsCacheExpires() {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of("20264"));
        givenPublishedQuarter("20264");
        givenQuarterCollection("20264");

        refresh.refreshSales(TODAY);

        verify(cache).hasSalesQuarterWithTtl("20264", MINIMUM_TTL);
        verify(cache).saveSalesQuarter("20264", Map.of(AREA + ":" + INDUSTRY, statistics("20264")));
        verify(cache, never()).publishLatestSalesQuarter(anyString());
    }

    @Test
    void aFailedCacheWriteLeavesThePreviousQuarterReadable() {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.of("20263"));
        when(cache.findPublishedSales("20263", AREA, INDUSTRY))
                .thenReturn(new SalesLookup(true, Optional.of(statistics("20263"))));
        givenPublishedQuarter("20264");
        givenQuarterCollection("20264");
        doThrow(new BusinessException(COMMERCIAL_STATS_CACHE_FAILED))
                .when(cache).saveSalesQuarter(eq("20264"), any());

        BusinessException error = assertThrows(BusinessException.class, () -> refresh.refreshSales(TODAY));

        assertEquals(COMMERCIAL_STATS_CACHE_FAILED, error.getErrorCode());
        verify(cache, never()).publishLatestSalesQuarter(anyString());
        assertEquals("20263", sales.findLatestStatistics(AREA, INDUSTRY).orElseThrow().quarterCode());
    }

    @Test
    void anUpstreamErrorStopsTheProbeWithoutTryingOlderQuarters() {
        when(client.fetchPage(SERVICE, 1, 1, "20264"))
                .thenThrow(new BusinessException(SEOUL_API_REQUEST_FAILED));

        BusinessException error = assertThrows(BusinessException.class, () -> refresh.refreshSales(TODAY));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
        verify(client).fetchPage(SERVICE, 1, 1, "20264");
        verifyNoMoreInteractions(client);
        verify(cache, never()).publishLatestSalesQuarter(anyString());
    }

    @Test
    void rejectsAProbeThatReturnsADifferentQuarter() {
        when(client.fetchPage(SERVICE, 1, 1, "20264"))
                .thenReturn(new SeoulOpenApiClient.Page(1, List.of(salesRow("20263"))));

        BusinessException error = assertThrows(BusinessException.class, () -> refresh.refreshSales(TODAY));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
        verify(cache, never()).publishLatestSalesQuarter(anyString());
    }

    @Test
    void latestLookupDistinguishesAnUnavailableQuarterFromAMissingAreaAndIndustry() {
        when(cache.findLatestSalesQuarter()).thenReturn(Optional.empty()).thenReturn(Optional.of("20263"));
        when(cache.findPublishedSales("20263", AREA, INDUSTRY))
                .thenReturn(new SalesLookup(false, Optional.empty()), new SalesLookup(true, Optional.empty()));

        assertEquals(SALES_QUARTER_NOT_READY,
                assertThrows(BusinessException.class, () -> sales.findLatestStatistics(AREA, INDUSTRY)).getErrorCode());
        assertEquals(SALES_QUARTER_NOT_READY,
                assertThrows(BusinessException.class, () -> sales.findLatestStatistics(AREA, INDUSTRY)).getErrorCode());
        assertTrue(sales.findLatestStatistics(AREA, INDUSTRY).isEmpty());
        verifyNoInteractions(client);
    }

    private void givenPublishedQuarter(String quarterCode) {
        when(client.fetchPage(SERVICE, 1, 1, quarterCode))
                .thenReturn(new SeoulOpenApiClient.Page(1, List.of(salesRow(quarterCode))));
    }

    private void givenQuarterCollection(String quarterCode) {
        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            pageConsumer.accept(List.of(salesRow(quarterCode)));
            return null;
        }).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(quarterCode));
    }

    private JsonNode salesRow(String quarterCode) {
        return objectMapper.readTree("""
                {"STDR_YYQU_CD":"%s", "TRDAR_CD":"%s", "SVC_INDUTY_CD":"%s", "THSMON_SELNG_AMT":100}
                """.formatted(quarterCode, AREA, INDUSTRY));
    }

    private EstimatedSalesStatistics statistics(String quarterCode) {
        return new EstimatedSalesStatistics(quarterCode, AREA, INDUSTRY, BigDecimal.valueOf(100), null, null, null);
    }
}
