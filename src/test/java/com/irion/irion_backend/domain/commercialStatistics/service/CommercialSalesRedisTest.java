package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SALES_QUARTER_NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "RUN_COMMERCIAL_REDIS_TESTS", matches = "true")
class CommercialSalesRedisTest {

    private static final String QUARTER = "99991";
    private static final String SERVICE_NAME = "VwsmTrdarSelngQq";
    private static final String CACHE_KEY = "commercial-stats:v1:sales:{99991}";
    private static final String LATEST_QUARTER_KEY = "commercial-stats:v1:sales:latest-ready-quarter";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SeoulOpenApiClient client;
    private CommercialStatisticsCacheRepository repository;
    private CommercialSalesService service;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 6380);
        config.setDatabase(15);

        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.delete(CACHE_KEY);

        client = mock(SeoulOpenApiClient.class);
        repository = new CommercialStatisticsCacheRepository(redisTemplate, objectMapper);
        service = new CommercialSalesService(client, repository);
    }

    @AfterEach
    void tearDown() {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(List.of(CACHE_KEY, LATEST_QUARTER_KEY));
            }
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    @Test
    void concurrentRequestsShareOneCollectionAndReuseTheQuarterUntilExpiry() throws Exception {
        CountDownLatch collectionStarted = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        CountDownLatch allowCollectionToFinish = new CountDownLatch(1);

        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            collectionStarted.countDown();
            assertTrue(allowCollectionToFinish.await(10, TimeUnit.SECONDS));
            pageConsumer.accept(List.of(salesRow("3130167", "CS300011", "796270824.125")));
            pageConsumer.accept(List.of(salesRow("3130167", "CS100010", "50000000.25")));
            return null;
        }).when(client).forEachPage(eq(SERVICE_NAME), eq(1000), any(), eq(QUARTER));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<EstimatedSalesStatistics>> first = executor.submit(
                    () -> service.findStatistics(QUARTER, "3130167", "CS300011")
            );
            assertTrue(collectionStarted.await(10, TimeUnit.SECONDS));

            Future<Optional<EstimatedSalesStatistics>> second = executor.submit(() -> {
                secondRequestStarted.countDown();
                return service.findStatistics(QUARTER, "3130167", "CS100010");
            });
            assertTrue(secondRequestStarted.await(10, TimeUnit.SECONDS));
            allowCollectionToFinish.countDown();

            assertEquals(0, new BigDecimal("796270824.125").compareTo(
                    first.get(10, TimeUnit.SECONDS).orElseThrow().reportedSalesAmountWon()
            ));
            assertEquals(0, new BigDecimal("50000000.25").compareTo(
                    second.get(10, TimeUnit.SECONDS).orElseThrow().reportedSalesAmountWon()
            ));
        } finally {
            allowCollectionToFinish.countDown();
            executor.shutdownNow();
        }

        EstimatedSalesStatistics cached = service.findStatistics(QUARTER, "3130167", "CS300011").orElseThrow();
        assertEquals(0, new BigDecimal("796270824.125").compareTo(cached.reportedSalesAmountWon()));
        assertTrue(service.findStatistics(QUARTER, "3130167", "CS100001").isEmpty());
        assertTrue(redisTemplate.getExpire(CACHE_KEY) > 0);
        verify(client, times(1)).forEachPage(eq(SERVICE_NAME), eq(1000), any(), eq(QUARTER));

        redisTemplate.expire(CACHE_KEY, Duration.ZERO);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY)));
        assertTrue(service.findStatistics(QUARTER, "3130167", "CS300011").isPresent());
        verify(client, times(2)).forEachPage(eq(SERVICE_NAME), eq(1000), any(), eq(QUARTER));
    }

    @Test
    void aFailedCollectionDoesNotPublishPartialDataAndCanBeRetried() {
        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            pageConsumer.accept(List.of(salesRow("3130167", "CS300011", "100.25")));
            throw new BusinessException(SEOUL_API_REQUEST_FAILED);
        }).doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            pageConsumer.accept(List.of(salesRow("3130167", "CS300011", "200.25")));
            return null;
        }).when(client).forEachPage(eq(SERVICE_NAME), eq(1000), any(), eq(QUARTER));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.findStatistics(QUARTER, "3130167", "CS300011"));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY)));

        EstimatedSalesStatistics retried = service.findStatistics(QUARTER, "3130167", "CS300011").orElseThrow();
        assertEquals(0, new BigDecimal("200.25").compareTo(retried.reportedSalesAmountWon()));
    }

    @Test
    void latestQuarterCanBeReadFromRedisAndAnEmptyReplacementIsUnavailable() {
        redisTemplate.delete(LATEST_QUARTER_KEY);
        String areaCode = "3130167";
        String industryCode = "CS300011";
        EstimatedSalesStatistics statistics = new EstimatedSalesStatistics(
                QUARTER, areaCode, industryCode, new BigDecimal("100.25"), null, null, null
        );

        repository.saveSalesQuarter(QUARTER, Map.of(areaCode + ":" + industryCode, statistics));
        assertTrue(repository.hasSalesQuarterWithTtl(QUARTER, Duration.ofDays(2)));
        assertTrue(repository.publishLatestSalesQuarter(QUARTER));
        assertFalse(repository.publishLatestSalesQuarter("99984"));
        assertEquals(Optional.of(QUARTER), repository.findLatestSalesQuarter());
        assertEquals(statistics, service.findLatestStatistics(areaCode, industryCode).orElseThrow());

        repository.saveSalesQuarter(QUARTER, Map.of());
        assertFalse(repository.hasSalesQuarterWithTtl(QUARTER, Duration.ZERO));
        assertTrue(repository.findSales(QUARTER, areaCode, industryCode).loaded());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.findLatestStatistics(areaCode, industryCode));
        assertEquals(SALES_QUARTER_NOT_READY, error.getErrorCode());
    }

    private JsonNode salesRow(String areaCode, String industryCode, String salesAmount) {
        return objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree("""
                        {
                          "STDR_YYQU_CD": "%s",
                          "TRDAR_CD": "%s",
                          "SVC_INDUTY_CD": "%s",
                          "THSMON_SELNG_AMT": %s
                        }
                        """.formatted(QUARTER, areaCode, industryCode, salesAmount));
    }
}
