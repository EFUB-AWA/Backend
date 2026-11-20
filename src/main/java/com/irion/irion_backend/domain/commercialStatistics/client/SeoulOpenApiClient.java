package com.irion.irion_backend.domain.commercialStatistics.client;

import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.global.error.BusinessException;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.*;

@Slf4j
@Component
public class SeoulOpenApiClient {

    private static final int MAX_PAGE_SIZE = 1000;
    private static final String SUCCESS_CODE = "INFO-000";
    private static final String NO_DATA_CODE = "INFO-200";

    private final SeoulOpenApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SeoulOpenApiClient(SeoulOpenApiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                // 인증키가 URL 경로에 있으므로 이 클라이언트의 HTTP 관측을 비활성화
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    public Page fetchPage(String serviceName, int startIndex, int endIndex, String... filters) {
        if (startIndex < 1 || endIndex < startIndex || endIndex - startIndex >= MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("한 페이지는 1부터 시작하며 최대 1000행까지 요청할 수 있습니다.");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new BusinessException(SEOUL_API_KEY_MISSING);
        }

        URI uri = buildUri(serviceName, startIndex, endIndex, filters);
        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            return parsePage(serviceName, startIndex, endIndex, body);
        } catch (RestClientException e) {
            // HTTP 예외 메시지에 인증키가 포함된 URL이 들어갈 수 있음
            log.warn("서울시 Open API HTTP 호출 실패. service={}, start={}, end={}, type={}", serviceName, startIndex, endIndex, e.getClass().getSimpleName());

            throw new BusinessException(SEOUL_API_REQUEST_FAILED);
        } catch (JacksonException e) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
    }

    public void forEachPage(String serviceName, int pageSize, Consumer<List<JsonNode>> pageConsumer, String... filters) {
        Page firstPage = fetchPage(serviceName, 1, pageSize, filters);
        if (firstPage.rows().isEmpty()) {
            return;
        }

        int totalCount = firstPage.totalCount();
        pageConsumer.accept(firstPage.rows());

        for (long start = pageSize + 1L; start <= totalCount; start += pageSize) {
            int end = (int) Math.min(start + pageSize - 1L, totalCount);
            Page page = fetchPage(serviceName, (int) start, end, filters);

            if (page.totalCount() != totalCount) {
                throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
            }
            pageConsumer.accept(page.rows());
        }
    }

    private URI buildUri(String serviceName, int startIndex, int endIndex, String[] filters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .pathSegment("{key}", "json", "{service}", "{start}", "{end}");

        List<Object> values = new ArrayList<>(List.of(properties.apiKey(), serviceName, startIndex, endIndex));

        for (int i = 0; i < filters.length; i++) {
            if (filters[i] == null || filters[i].isBlank()) {
                throw new IllegalArgumentException("서울시 Open API 필터에는 빈 값을 전달할 수 없습니다.");
            }
            builder.pathSegment("{filter" + i + "}");
            values.add(filters[i]);
        }

        return builder.encode()
                .buildAndExpand(values.toArray())
                .toUri();
    }

    private Page parsePage(String serviceName, int startIndex, int endIndex, String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        JsonNode root = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(body);
        JsonNode service = root.path(serviceName);
        JsonNode result = service.path("RESULT");
        if (result.isMissingNode()) {
            result = root.path("RESULT");
        }

        String code = result.path("CODE").asText();
        if (NO_DATA_CODE.equals(code)) {
            return new Page(0, List.of());
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }
        if (!SUCCESS_CODE.equals(code)) {
            throw new BusinessException(SEOUL_API_REQUEST_FAILED);
        }
        if (!service.isObject()) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        int totalCount;
        try {
            totalCount = Integer.parseInt(service.path("list_total_count").asText());
        } catch (NumberFormatException e) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        JsonNode rows = service.path("row");
        if (totalCount == 0 && (rows.isMissingNode() || rows.isNull() || (rows.isArray() && rows.isEmpty()))) {
            return new Page(0, List.of());
        }

        int expectedSize = Math.min(endIndex, totalCount) - startIndex + 1;
        if (totalCount < 0 || expectedSize <= 0 || !rows.isArray() || rows.size() != expectedSize) {
            throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
        }

        List<JsonNode> pageRows = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new BusinessException(SEOUL_API_INVALID_RESPONSE);
            }
            pageRows.add(row);
        }
        return new Page(totalCount, List.copyOf(pageRows));
    }

    public record Page(int totalCount, List<JsonNode> rows) {}
}
