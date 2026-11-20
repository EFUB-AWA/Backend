package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.ConsumptionStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

class CommercialConsumptionServiceTest {

    private static final String SERVICE = "trdarNcmCnsmp";
    private static final String QUARTER = "20262";
    private static final String AREA = "3130167";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final CommercialConsumptionService service = new CommercialConsumptionService(client);

    @Test
    void findsTheAreaInLaterPagesAndPreservesDecimalZeroAndMissingValues() {
        JsonNode selected = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree("""
                        {
                          "STDR_YYQU_CD": 20262,
                          "TRDAR_CD": 3130167,
                          "EXPNDTR_TOTAMT": 5000000000.75,
                          "FDSTFFS_EXPNDTR_TOTAMT": "10",
                          "CLTHS_FTWR_EXPNDTR_TOTAMT": 20,
                          "LVSPL_EXPNDTR_TOTAMT": 30,
                          "MCP_EXPNDTR_TOTAMT": 40,
                          "TRNSPORT_EXPNDTR_TOTAMT": 50,
                          "LSR_EXPNDTR_TOTAMT": 60,
                          "CLTUR_EXPNDTR_TOTAMT": 70,
                          "EDC_EXPNDTR_TOTAMT": 0,
                          "PLESR_EXPNDTR_TOTAMT": null
                        }
                        """);
        givenRows(row(QUARTER, "3130168"), selected);

        ConsumptionStatistics result = service.findStatistics(QUARTER, AREA).orElseThrow();

        verify(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER));
        assertEquals(new ConsumptionStatistics(QUARTER, AREA, new BigDecimal("5000000000.75"),
                BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(30), BigDecimal.valueOf(40),
                BigDecimal.valueOf(50), BigDecimal.valueOf(60), BigDecimal.valueOf(70), BigDecimal.ZERO, null), result);
    }

    @Test
    void returnsEmptyWhenTheAreaHasNoRow() {
        givenRows(row(QUARTER, "3130168"));

        assertTrue(service.findStatistics(QUARTER, AREA).isEmpty());
    }

    @Test
    void rejectsRowsFromAnotherQuarter() {
        givenRows(row("20261", AREA));

        BusinessException error = assertThrows(BusinessException.class, () -> service.findStatistics(QUARTER, AREA));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    @Test
    void rejectsDuplicateRowsForTheSameArea() {
        JsonNode duplicate = row(QUARTER, AREA);
        givenRows(duplicate, duplicate);

        BusinessException error = assertThrows(BusinessException.class, () -> service.findStatistics(QUARTER, AREA));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    @Test
    void propagatesUpstreamErrors() {
        doThrow(new BusinessException(SEOUL_API_REQUEST_FAILED)).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER));

        BusinessException error = assertThrows(BusinessException.class, () -> service.findStatistics(QUARTER, AREA));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SEOUL_CONSUMPTION_LIVE", matches = "true")
    void readsRealConsumptionForTheSalesExample() {
        String apiKey = System.getenv("SEOUL_OPEN_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "SEOUL_OPEN_API_KEY 환경변수를 테스트 실행 설정에 등록하세요.");

        String baseUrl = System.getenv().getOrDefault("SEOUL_OPEN_API_BASE_URL", "http://openapi.seoul.go.kr:8088");
        SeoulOpenApiProperties properties = new SeoulOpenApiProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        SeoulOpenApiClient liveClient = new SeoulOpenApiClient(properties, objectMapper, RestClient.builder());
        ConsumptionStatistics result = new CommercialConsumptionService(liveClient).findStatistics(QUARTER, AREA)
                .orElseThrow(() -> new AssertionError("추정매출 예시와 같은 분기·상권의 소비 통계가 없습니다."));

        assertNotNull(result.totalExpenditure());
        System.out.println(result);
    }

    private void givenRows(JsonNode... rows) {
        doAnswer(invocation -> {
            Consumer<List<JsonNode>> pageConsumer = invocation.getArgument(2);
            for (JsonNode row : rows) {
                pageConsumer.accept(List.of(row));
            }
            return null;
        }).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER));
    }

    private JsonNode row(String quarterCode, String areaCode) {
        return objectMapper.readTree("""
                {"STDR_YYQU_CD":"%s", "TRDAR_CD":"%s"}
                """.formatted(quarterCode, areaCode));
    }
}
