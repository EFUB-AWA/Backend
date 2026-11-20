package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.FloatingPopulationStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.DatasetState;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository.SnapshotLookup;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.AVAILABLE;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.NO_DATA;
import static com.irion.irion_backend.domain.commercialStatistics.dto.CommercialStatisticsResponse.DatasetStatus.UNAVAILABLE;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_REQUEST_FAILED;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.CONSUMPTION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.FLOATING_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.RESIDENT_POPULATION;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.SALES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.STORES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.WORKER_POPULATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommercialStatisticsServiceTest {

    private static final String QUARTER = "20262";
    private static final String AREA = "3130167";
    private static final String INDUSTRY = "CS300011";

    private final CommercialStatisticsCacheRepository cache = mock(CommercialStatisticsCacheRepository.class);
    private final CommercialStoreService storeService = mock(CommercialStoreService.class);
    private final CommercialStatisticsService service = new CommercialStatisticsService(cache, storeService);

    @Test
    void returnsTheActualQuarterAndCalculatesReferenceSalesFromMatchedStoreCounts() {
        when(cache.findDatasetState(any())).thenReturn(new DatasetState(null, false));
        when(cache.findDatasetState(SALES)).thenReturn(new DatasetState(QUARTER, true));
        when(cache.findQuarterStatistics(SALES, QUARTER, AREA + ":" + INDUSTRY, EstimatedSalesStatistics.class))
                .thenReturn(availableSales());
        when(cache.findDatasetState(STORES)).thenReturn(new DatasetState(QUARTER, false));
        StoreStatistics stores = stores(QUARTER, 24L);
        when(cache.findStoreStatistics(QUARTER, AREA, INDUSTRY))
                .thenReturn(new SnapshotLookup<>(true, Optional.of(stores)));

        var result = service.getStatistics(AREA, INDUSTRY);

        assertEquals(QUARTER, result.estimatedSales().quarterCode());
        assertEquals(AVAILABLE, result.estimatedSales().status());
        assertTrue(result.estimatedSales().stale());
        assertEquals(new BigDecimal("33177951.00"), result.estimatedSalesPerStoreWon());
        verify(storeService, never()).collectAreaStatistics(any(), any());
    }

    @Test
    void distinguishesNoDataNotReadyAndUpstreamFailureWithoutDiscardingOtherResults() {
        when(cache.findDatasetState(any())).thenReturn(new DatasetState(null, false));
        when(cache.findDatasetState(FLOATING_POPULATION)).thenReturn(new DatasetState(QUARTER, false));
        when(cache.findQuarterStatistics(FLOATING_POPULATION, QUARTER, AREA, FloatingPopulationStatistics.class))
                .thenReturn(new SnapshotLookup<>(true, Optional.empty()));
        when(cache.findDatasetState(RESIDENT_POPULATION)).thenReturn(new DatasetState(null, true));
        when(cache.findDatasetState(STORES)).thenReturn(new DatasetState(QUARTER, false));
        when(cache.findStoreStatistics(QUARTER, AREA, INDUSTRY)).thenReturn(new SnapshotLookup<>(false, Optional.empty()));
        when(storeService.collectAreaStatistics(QUARTER, AREA))
                .thenThrow(new BusinessException(SEOUL_API_REQUEST_FAILED));

        var result = service.getStatistics(AREA, INDUSTRY);

        assertEquals(NO_DATA, result.floatingPopulation().status());
        assertEquals(QUARTER, result.floatingPopulation().quarterCode());
        assertEquals(UNAVAILABLE, result.residentPopulation().status());
        assertEquals(UNAVAILABLE, result.stores().status());
        assertEquals(QUARTER, result.stores().quarterCode());
        assertEquals(NOT_READY, result.workerPopulation().status());
        assertNull(result.estimatedSalesPerStoreWon());
    }

    @Test
    void cachesAllIndustriesForTheRequestedAreaAndDoesNotDivideAcrossQuarters() {
        when(cache.findDatasetState(any())).thenReturn(new DatasetState(null, false));
        when(cache.findDatasetState(SALES)).thenReturn(new DatasetState(QUARTER, false));
        when(cache.findQuarterStatistics(SALES, QUARTER, AREA + ":" + INDUSTRY, EstimatedSalesStatistics.class))
                .thenReturn(availableSales());
        String storeQuarter = "20263";
        when(cache.findDatasetState(STORES)).thenReturn(new DatasetState(storeQuarter, false));
        when(cache.findStoreStatistics(storeQuarter, AREA, INDUSTRY)).thenReturn(new SnapshotLookup<>(false, Optional.empty()));
        Map<String, StoreStatistics> areaStores = Map.of(INDUSTRY, stores(storeQuarter, 24L),
                "CS100010", new StoreStatistics(storeQuarter, AREA, "CS100010", 20L, null, null, null, null, null, null));
        when(storeService.collectAreaStatistics(storeQuarter, AREA)).thenReturn(areaStores);

        var result = service.getStatistics(AREA, INDUSTRY);

        verify(cache, times(2)).findStoreStatistics(storeQuarter, AREA, INDUSTRY);
        verify(cache).saveStoreArea(storeQuarter, AREA, areaStores);
        assertEquals(24L, result.stores().data().totalStoreCount());
        assertNull(result.estimatedSalesPerStoreWon());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SEOUL_COMMERCIAL_STATISTICS_LIVE", matches = "true")
    void preparesEachDatasetAndReturnsTheSixResultsUsingOnlyAreaAndIndustry() {
        String apiKey = System.getenv("SEOUL_OPEN_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "SEOUL_OPEN_API_KEY 환경변수를 테스트 실행 설정에 등록하세요.");
        String baseUrl = System.getenv().getOrDefault("SEOUL_OPEN_API_BASE_URL", "http://openapi.seoul.go.kr:8088");
        var properties = new SeoulOpenApiProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        var objectMapper = new ObjectMapper();
        var client = new SeoulOpenApiClient(properties, objectMapper, RestClient.builder());

        var redisConfig = new RedisStandaloneConfiguration("127.0.0.1", 6380);
        redisConfig.setDatabase(15);
        var connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        var redis = new StringRedisTemplate(connectionFactory);
        String prefix = "commercial-statistics-test:" + UUID.randomUUID() + ":";
        RedisSerializer<String> strings = RedisSerializer.string();
        redis.setKeySerializer(new RedisSerializer<String>() {
            @Override
            public byte[] serialize(String value) {
                return strings.serialize(prefix + value);
            }

            @Override
            public String deserialize(byte[] bytes) {
                return strings.deserialize(bytes).substring(prefix.length());
            }
        });

        try {
            var redisCache = new CommercialStatisticsCacheRepository(redis, objectMapper);
            var salesRefresh = new CommercialSalesRefreshService(new CommercialSalesService(client, redisCache), redisCache, properties);
            var otherRefresh = new CommercialStatisticsRefreshService(client, properties, redisCache,
                    new CommercialFloatingPopulationService(client), new CommercialResidentPopulationService(client),
                    new CommercialWorkerPopulationService(client), new CommercialConsumptionService(client));
            LocalDate sampleDate = LocalDate.of(2026, 6, 30);
            salesRefresh.refreshSales(sampleDate);
            otherRefresh.refreshStatistics(sampleDate);
            var response = new CommercialStatisticsService(redisCache, new CommercialStoreService(client))
                    .getStatistics(AREA, INDUSTRY);

            assertEquals(AVAILABLE, response.estimatedSales().status(), SALES.name());
            assertEquals(AVAILABLE, response.stores().status(), STORES.name());
            assertEquals(AVAILABLE, response.floatingPopulation().status(), FLOATING_POPULATION.name());
            assertEquals(AVAILABLE, response.residentPopulation().status(), RESIDENT_POPULATION.name());
            assertEquals(AVAILABLE, response.workerPopulation().status(), WORKER_POPULATION.name());
            assertEquals(AVAILABLE, response.historicalConsumption().status(), CONSUMPTION.name());
            assertEquals(QUARTER, response.estimatedSales().quarterCode());
            assertEquals(QUARTER, response.stores().quarterCode());
            assertTrue(response.estimatedSalesPerStoreWon().signum() > 0);
            var cachedResponse = new CommercialStatisticsService(redisCache, storeService).getStatistics(AREA, INDUSTRY);
            assertEquals(response, cachedResponse);
            verify(storeService, never()).collectAreaStatistics(any(), any());
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
        } finally {
            try {
                var keys = redis.keys("commercial-stats:v1:*");
                if (keys != null && !keys.isEmpty()) {
                    redis.delete(keys);
                }
            } finally {
                connectionFactory.destroy();
            }
        }
    }

    private SnapshotLookup<EstimatedSalesStatistics> availableSales() {
        return new SnapshotLookup<>(true, Optional.of(new EstimatedSalesStatistics(QUARTER, AREA, INDUSTRY,
                BigDecimal.valueOf(796270824), null, null, null)));
    }

    private StoreStatistics stores(String quarter, Long count) {
        return new StoreStatistics(quarter, AREA, INDUSTRY, count, null, null, null, null, null, null);
    }
}
