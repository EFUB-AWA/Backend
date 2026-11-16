package com.irion.irion_backend.domain.costbenchmark.service;

import com.irion.irion_backend.domain.costbenchmark.dto.KosisDataRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KosisOperatingCostParser {

    private static final Set<String> COST_ITEMS =
            Set.of("T03", "T04", "T05", "T06");

    private final ObjectMapper objectMapper;

    public List<KosisDataRow> parseRows(String json) {
        return objectMapper.readValue(
                json,
                new TypeReference<List<KosisDataRow>>() {}
        );
    }

    public Map<String, BigDecimal> parseItems(
            List<KosisDataRow> rows,
            String regionCode,
            String industryCode,
            Set<String> itemIds
    ) {
        return rows.stream()
                .filter(row -> regionCode.equals(row.regionCode()))
                .filter(row -> industryCode.equals(row.industryCode()))
                .filter(row -> itemIds.contains(row.itemId()))
                .filter(row -> row.value() != null)
                .filter(row -> row.value().matches("\\d+(\\.\\d+)?"))
                .collect(Collectors.toMap(
                        KosisDataRow::itemId,
                        row -> new BigDecimal(row.value()),
                        (first, second) -> first
                ));
    }

    public Map<String, BigDecimal> parseOperatingCosts(
            List<KosisDataRow> rows,
            String regionCode,
            String industryCode
    ) {
        return parseItems(
                rows,
                regionCode,
                industryCode,
                COST_ITEMS
        );
    }
}
