package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommercialSalesServiceTest {

    private static final String SERVICE_NAME = "VwsmTrdarSelngQq";
    private static final String QUARTER = "20262";
    private static final String AREA_CODE = "3110008";
    private static final String INDUSTRY_CODE = "CS100010";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findsTheRowMatchingBothCodesAndPreservesZeroAndNull() {
        CommercialSalesService service = serviceWithPages(
                """
                {"STDR_YYQU_CD":"20262","TRDAR_CD":"3110008","SVC_INDUTY_CD":"CS100001"}
                """,
                """
                {"STDR_YYQU_CD":"20262","TRDAR_CD":"3110009","SVC_INDUTY_CD":"CS100010"}
                """,
                """
                {
                  "STDR_YYQU_CD":"20262",
                  "TRDAR_CD":"3110008",
                  "SVC_INDUTY_CD":"CS100010",
                  "THSMON_SELNG_AMT":5000000000.75,
                  "THSMON_SELNG_CO":"25",
                  "MDWK_SELNG_AMT":0,
                  "WKEND_SELNG_AMT":null
                }
                """
        );

        EstimatedSalesStatistics result = service
                .findStatistics(QUARTER, AREA_CODE, INDUSTRY_CODE)
                .orElseThrow();

        assertEquals(QUARTER, result.quarterCode());
        assertEquals(AREA_CODE, result.commercialAreaCode());
        assertEquals(INDUSTRY_CODE, result.industryCode());
        assertEquals(0, new BigDecimal("5000000000.75").compareTo(result.reportedSalesAmountWon()));
        assertEquals(Long.valueOf(25), result.reportedSalesTransactionCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.reportedWeekdaySalesAmountWon()));
        assertNull(result.reportedWeekendSalesAmountWon());
    }

    @Test
    void returnsEmptyWhenTheAreaAndIndustryCombinationDoesNotExist() {
        CommercialSalesService service = serviceWithPages("""
                {"STDR_YYQU_CD":"20262","TRDAR_CD":"3110008","SVC_INDUTY_CD":"CS100001"}
                """);

        assertTrue(service.findStatistics(QUARTER, AREA_CODE, INDUSTRY_CODE).isEmpty());
    }

    @Test
    void rejectsAResponseFromADifferentQuarter() {
        CommercialSalesService service = serviceWithPages("""
                {"STDR_YYQU_CD":"20261","TRDAR_CD":"3110008","SVC_INDUTY_CD":"CS100010"}
                """);

        BusinessException error = assertThrows(BusinessException.class, () -> service.findStatistics(QUARTER, AREA_CODE, INDUSTRY_CODE));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SEOUL_SALES_LIVE", matches = "true")
    void readsRealSalesFor20262() {
        String apiKey = System.getenv("SEOUL_OPEN_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "SEOUL_OPEN_API_KEY 환경변수를 테스트 실행 설정에 등록하세요.");

        String baseUrl = System.getenv().getOrDefault("SEOUL_OPEN_API_BASE_URL", "http://openapi.seoul.go.kr:8088");
        SeoulOpenApiProperties properties = new SeoulOpenApiProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        SeoulOpenApiClient client = new SeoulOpenApiClient(properties, objectMapper, RestClient.builder());

        SeoulOpenApiClient.Page firstPage = client.fetchPage(SERVICE_NAME, 1, 1, QUARTER);
        assertFalse(firstPage.rows().isEmpty(), "20262 실응답에 행이 없습니다.");

        JsonNode row = firstPage.rows().get(0);
        assertEquals(QUARTER, row.path("STDR_YYQU_CD").asText());

        String areaCode = row.path("TRDAR_CD").asText();
        String industryCode = row.path("SVC_INDUTY_CD").asText();

        EstimatedSalesStatistics result = serviceWithEmptyCache(client)
                .findStatistics(QUARTER, areaCode, industryCode)
                .orElseThrow();

        assertEquals(QUARTER, result.quarterCode());
        assertEquals(areaCode, result.commercialAreaCode());
        assertEquals(industryCode, result.industryCode());

        System.out.println("추정매출 분기 전체 행 수: " + firstPage.totalCount());
        System.out.println(result);
    }

    private CommercialSalesService serviceWithPages(String... rows) {
        SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            for (String row : rows) {
                pageConsumer.accept(List.of(objectMapper.readTree(row)));
            }
            return null;
        }).when(client).forEachPage(eq(SERVICE_NAME), eq(1000), any(), eq(QUARTER));

        return serviceWithEmptyCache(client);
    }

    private CommercialSalesService serviceWithEmptyCache(SeoulOpenApiClient client) {
        CommercialStatisticsCacheRepository cacheRepository = mock(CommercialStatisticsCacheRepository.class);
        when(cacheRepository.findSales(anyString(), anyString(), anyString()))
                .thenReturn(new CommercialStatisticsCacheRepository.SalesLookup(false, Optional.empty()));

        return new CommercialSalesService(client, cacheRepository);
    }
}