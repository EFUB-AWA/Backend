package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommercialStoreServiceTest {

    private static final String SERVICE = "VwsmTrdarStorQq";
    private static final String QUARTER = "20262";
    private static final String AREA = "3130167";
    private static final String INDUSTRY = "CS300011";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final CommercialStoreService storeService = new CommercialStoreService(client);

    @Test
    void sendsQuarterAndAreaAsFiltersAndFindsTheIndustryInLaterPages() {
        JsonNode selected = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree("""
                        {
                          "STDR_YYQU_CD": 20262,
                          "TRDAR_CD": 3130167,
                          "SVC_INDUTY_CD": "CS300011",
                          "SIMILR_INDUTY_STOR_CO": 12,
                          "STOR_CO": "9",
                          "FRC_STOR_CO": 3,
                          "OPBIZ_STOR_CO": 0,
                          "CLSBIZ_STOR_CO": 1,
                          "OPBIZ_RT": 0,
                          "CLSBIZ_RT": 8.33
                        }
                        """);
        givenRows(row(QUARTER, AREA, "CS100010"), selected);

        StoreStatistics result = storeService.findStatistics(QUARTER, AREA, INDUSTRY).orElseThrow();

        verify(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER), eq(AREA));
        assertEquals(new StoreStatistics(QUARTER, AREA, INDUSTRY, 12L, 9L, 3L, 0L, 1L,
                BigDecimal.ZERO, new BigDecimal("8.33")), result);
    }

    @Test
    void returnsEmptyWhenNoRowMatchesTheIndustry() {
        givenRows(row(QUARTER, AREA, "CS100010"));

        assertTrue(storeService.findStatistics(QUARTER, AREA, INDUSTRY).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"20263, 3130167", "20262, 3130168"})
    void rejectsRowsOutsideTheRequestedQuarterAndArea(String responseQuarter, String responseArea) {
        givenRows(row(responseQuarter, responseArea, INDUSTRY));

        BusinessException error = assertThrows(BusinessException.class, () -> storeService.findStatistics(QUARTER, AREA, INDUSTRY));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    @Test
    void rejectsDuplicateRowsForTheSameIndustry() {
        JsonNode storeRow = row(QUARTER, AREA, INDUSTRY);
        givenRows(storeRow, storeRow);

        BusinessException error = assertThrows(BusinessException.class, () -> storeService.findStatistics(QUARTER, AREA, INDUSTRY));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    @Test
    void propagatesUpstreamErrors() {
        BusinessException upstreamError = new BusinessException(SEOUL_API_REQUEST_FAILED);
        doThrow(upstreamError).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER), eq(AREA));

        BusinessException error = assertThrows(BusinessException.class, () -> storeService.findStatistics(QUARTER, AREA, INDUSTRY));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SEOUL_STORES_LIVE", matches = "true")
    void readsRealStoreStatisticsForTheSalesExample() {
        String apiKey = System.getenv("SEOUL_OPEN_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "SEOUL_OPEN_API_KEY 환경변수를 테스트 실행 설정에 등록하세요.");

        String baseUrl = System.getenv().getOrDefault("SEOUL_OPEN_API_BASE_URL", "http://openapi.seoul.go.kr:8088");
        SeoulOpenApiProperties properties = new SeoulOpenApiProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        SeoulOpenApiClient liveClient = new SeoulOpenApiClient(properties, objectMapper, RestClient.builder());
        StoreStatistics result = new CommercialStoreService(liveClient).findStatistics(QUARTER, AREA, INDUSTRY)
                .orElseThrow(() -> new AssertionError("추정매출 예시와 같은 분기·상권·업종의 점포 통계가 없습니다."));

        assertNotNull(result.totalStoreCount());
        assertTrue(result.totalStoreCount() >= 0);
        System.out.println(result);
    }

    private void givenRows(JsonNode... rows) {
        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            for (JsonNode row : rows) {
                pageConsumer.accept(List.of(row));
            }
            return null;
        }).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER), eq(AREA));
    }

    private JsonNode row(String quarterCode, String areaCode, String industryCode) {
        return objectMapper.readTree("""
                {"STDR_YYQU_CD":"%s", "TRDAR_CD":"%s", "SVC_INDUTY_CD":"%s"}
                """.formatted(quarterCode, areaCode, industryCode));
    }
}
