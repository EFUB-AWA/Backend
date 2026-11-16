package com.irion.irion_backend.domain.costbenchmark.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KosisDataRow(
        @JsonProperty("C1") String regionCode,
        @JsonProperty("C2") String industryCode,
        @JsonProperty("C2_NM") String industryName,
        @JsonProperty("ITM_ID") String itemId,
        @JsonProperty("DT") String value,
        @JsonProperty("PRD_DE") String referenceYear,
        @JsonProperty("UNIT_NM") String unitName
) {
}

