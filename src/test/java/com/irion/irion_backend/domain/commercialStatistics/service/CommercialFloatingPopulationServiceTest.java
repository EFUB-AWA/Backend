package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.dto.FloatingPopulationStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

class CommercialFloatingPopulationServiceTest {

    private static final String SERVICE = "VwsmTrdarFlpopQq";
    private static final String QUARTER = "20262";
    private static final String AREA = "3130167";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final CommercialFloatingPopulationService service = new CommercialFloatingPopulationService(client);

    @Test
    void sendsTheQuarterAndFindsTheAreaInLaterPages() {
        JsonNode selected = objectMapper.readTree("""
                {
                  "STDR_YYQU_CD": 20262,
                  "TRDAR_CD": 3130167,
                  "TOT_FLPOP_CO": 150,
                  "ML_FLPOP_CO": 70,
                  "FML_FLPOP_CO": "80",
                  "AGRDE_10_FLPOP_CO": 5,
                  "AGRDE_20_FLPOP_CO": 10,
                  "AGRDE_30_FLPOP_CO": 15,
                  "AGRDE_40_FLPOP_CO": 20,
                  "AGRDE_50_FLPOP_CO": 30,
                  "AGRDE_60_ABOVE_FLPOP_CO": 70,
                  "TMZON_00_06_FLPOP_CO": 0,
                  "TMZON_06_11_FLPOP_CO": 15,
                  "TMZON_11_14_FLPOP_CO": 30,
                  "TMZON_14_17_FLPOP_CO": 45,
                  "TMZON_17_21_FLPOP_CO": 60,
                  "TMZON_21_24_FLPOP_CO": null
                }
                """);
        givenRows(row(QUARTER, "3130168"), selected);

        FloatingPopulationStatistics result = service.findStatistics(QUARTER, AREA).orElseThrow();

        verify(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER));
        assertEquals(new FloatingPopulationStatistics(QUARTER, AREA, 150L, 70L, 80L,
                5L, 10L, 15L, 20L, 30L, 70L, 0L, 15L, 30L, 45L, 60L, null), result);
    }

    @Test
    void returnsEmptyWhenNoRowMatchesTheArea() {
        givenRows(row(QUARTER, "3130168"));

        assertTrue(service.findStatistics(QUARTER, AREA).isEmpty());
    }

    @Test
    void rejectsRowsFromAnotherQuarter() {
        givenRows(row("20263", AREA));

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
        BusinessException upstreamError = new BusinessException(SEOUL_API_REQUEST_FAILED);
        doThrow(upstreamError).when(client).forEachPage(eq(SERVICE), eq(1000), any(), eq(QUARTER));

        BusinessException error = assertThrows(BusinessException.class, () -> service.findStatistics(QUARTER, AREA));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SEOUL_FLOATING_POPULATION_LIVE", matches = "true")
    void readsRealFloatingPopulationForTheSalesExample() {
        String apiKey = System.getenv("SEOUL_OPEN_API_KEY");
        assertTrue(apiKey != null && !apiKey.isBlank(), "SEOUL_OPEN_API_KEY 환경변수를 테스트 실행 설정에 등록하세요.");

        String baseUrl = System.getenv().getOrDefault("SEOUL_OPEN_API_BASE_URL", "http://openapi.seoul.go.kr:8088");
        SeoulOpenApiProperties properties = new SeoulOpenApiProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
        SeoulOpenApiClient liveClient = new SeoulOpenApiClient(properties, objectMapper, RestClient.builder());
        FloatingPopulationStatistics result = new CommercialFloatingPopulationService(liveClient)
                .findStatistics(QUARTER, AREA)
                .orElseThrow(() -> new AssertionError("추정매출 예시와 같은 분기·상권의 유동인구 통계가 없습니다."));

        assertNotNull(result.totalFloatingPopulation());
        assertTrue(result.totalFloatingPopulation() >= 0);
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
