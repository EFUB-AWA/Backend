package com.irion.irion_backend.domain.costbenchmark.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KosisApiClientTest {

    @Autowired
    private KosisApiClient kosisApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchOperatingCosts() throws IOException {
        String response = kosisApiClient.fetchOperatingCosts();

        inspectAndSave(
                response,
                "DT_3ME0142.json"
        );
    }

    @Test
    void fetchSalesStatistics() throws IOException {
        String response = kosisApiClient.fetchSalesStatistics();

        inspectAndSave(
                response,
                "DT_3ME0144.json"
        );
    }

    private void inspectAndSave(
            String response,
            String fileName
    ) throws IOException {

        assertNotNull(response);
        assertFalse(response.isBlank());

        JsonNode root = objectMapper.readTree(response);

        assertTrue(root.isArray(), "KOSIS 응답이 배열이 아닙니다.");
        assertFalse(root.isEmpty(), "KOSIS 응답이 비어 있습니다.");

        Path directory = Path.of("build", "kosis");
        Files.createDirectories(directory);

        Path filePath = directory.resolve(fileName);

        Files.writeString(
                filePath,
                response,
                StandardCharsets.UTF_8
        );

        System.out.println("\n===== " + fileName + " =====");
        System.out.println("전체 데이터 건수: " + root.size());

        Set<String> regions = new HashSet<>();
        Set<String> items = new HashSet<>();
        Set<String> industries = new HashSet<>();

        int seoulCount = 0;

        for (JsonNode row : root) {

            String region = row.path("C1_NM").asText();
            String industry = row.path("C2_NM").asText();
            String itemId = row.path("ITM_ID").asText();
            String itemName = row.path("ITM_NM").asText();
            String unit = row.path("UNIT_NM").asText();

            regions.add(region);
            items.add(itemId + " : " + itemName + " (" + unit + ")");

            if ("서울특별시".equals(region) || "서울".equals(region)) {
                seoulCount++;
                industries.add(industry);
            }
        }

        System.out.println("\n[지역 목록]");
        regions.stream().sorted().forEach(System.out::println);

        System.out.println("\n[통계 항목 목록]");
        items.stream().sorted().forEach(System.out::println);

        System.out.println("\n[서울 데이터]");
        System.out.println("서울 데이터 건수: " + seoulCount);
        System.out.println("서울 산업 분류 수: " + industries.size());

        System.out.println("\n[서울 산업 분류 목록]");
        industries.stream().sorted().forEach(System.out::println);

        System.out.println("\n저장 위치: " + filePath.toAbsolutePath());
    }
}
