package com.irion.irion_backend.domain.commercialStatistics.Client;


import com.irion.irion_backend.domain.commercialStatistics.client.SeoulOpenApiClient;
import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.global.error.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_INVALID_RESPONSE;
import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SEOUL_API_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.*;

class SeoulOpenApiClientTest {

    private static final String SERVICE = "VwsmTrdarSelngQq";
    private static final String PATH_PREFIX = "/test-key/json/" + SERVICE;

    private final Map<String, String> responses = new ConcurrentHashMap<>();

    private HttpServer server;
    private SeoulOpenApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = responses.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        SeoulOpenApiProperties properties = new SeoulOpenApiProperties(
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );
        client = new SeoulOpenApiClient(properties, new ObjectMapper(), RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsPagesInOrderAndPassesTheQuarterFilter() {
        responses.put(PATH_PREFIX + "/1/2/20262", success(3, "{\"id\":1},{\"id\":2}"));
        responses.put(PATH_PREFIX + "/3/3/20262", success(3, "{\"id\":3}"));
        List<Integer> ids = new ArrayList<>();

        client.forEachPage(SERVICE, 2, rows -> rows.forEach(row -> ids.add(row.path("id").asInt())), "20262");

        assertEquals(List.of(1, 2, 3), ids);
    }

    @Test
    void explicitNoDataReturnsAnEmptyPage() {
        responses.put(PATH_PREFIX + "/1/1", "{\"RESULT\":{\"CODE\":\"INFO-200\"}}");

        SeoulOpenApiClient.Page page = client.fetchPage(SERVICE, 1, 1);

        assertEquals(0, page.totalCount());
        assertTrue(page.rows().isEmpty());
    }

    @Test
    void apiErrorInHttp200IsReportedAsFailure() {
        responses.put(PATH_PREFIX + "/1/1", "{\"RESULT\":{\"CODE\":\"INFO-100\"}}");

        BusinessException error = assertThrows(BusinessException.class, () -> client.fetchPage(SERVICE, 1, 1));

        assertEquals(SEOUL_API_REQUEST_FAILED, error.getErrorCode());
    }

    @Test
    void emptyPageAfterAValidFirstPageFailsTheCollection() {
        responses.put(PATH_PREFIX + "/1/1", success(2, "{\"id\":1}"));
        responses.put(PATH_PREFIX + "/2/2", "{\"RESULT\":{\"CODE\":\"INFO-200\"}}");

        BusinessException error = assertThrows(BusinessException.class, () -> client.forEachPage(SERVICE, 1, rows -> {}));

        assertEquals(SEOUL_API_INVALID_RESPONSE, error.getErrorCode());
    }

    private String success(int totalCount, String rows) {
        return """
                {"%s":{"list_total_count":%d,"RESULT":{"CODE":"INFO-000"},"row":[%s]}}
                """.formatted(SERVICE, totalCount, rows);
    }
}