package com.irion.irion_backend.domain.Region.dto;

import java.util.List;

public record RegionListResponse(
        List<RegionResponse> regions
) {
}
