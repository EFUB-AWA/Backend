package com.irion.irion_backend.domain.costbenchmark.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class KosisApiClient {

    private static final String API_PATH =
            "/openapi/Param/statisticsParameterData.do";

    private static final String OUTPUT_FIELDS =
            "ORG_ID TBL_ID TBL_NM OBJ_ID OBJ_NM " +
                    "OBJ_NM_ENG NM NM_ENG ITM_ID ITM_NM " +
                    "ITM_NM_ENG UNIT_NM UNIT_NM_ENG " +
                    "PRD_SE PRD_DE LST_CHN_DE";

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    public KosisApiClient(
            @Value("${kosis.base-url}") String baseUrl,
            @Value("${kosis.api-key}") String apiKey
    ) {
        this.restClient = RestClient.create();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String fetchData(
            String tableId,
            String itemIds,
            int year
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + API_PATH)
                .queryParam("method", "getList")
                .queryParam("apiKey", apiKey)
                .queryParam("orgId", "142")
                .queryParam("tblId", tableId)
                .queryParam("itmId", itemIds)
                .queryParam("objL1", "ALL")
                .queryParam("objL2", "ALL")
                .queryParam("format", "json")
                .queryParam("jsonVD", "Y")
                .queryParam("prdSe", "Y")
                .queryParam("startPrdDe", year)
                .queryParam("endPrdDe", year)
                .queryParam("outputFields", OUTPUT_FIELDS)
                .encode()
                .build()
                .toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

    public String fetchOperatingCosts() {
        return fetchData(
                "DT_3ME0144",
                "T03 T04 T05 T06",
                2023
        );
    }

    public String fetchSalesStatistics() {
        return fetchData(
                "DT_3ME0142",
                "T001",
                2023
        );
    }

}

