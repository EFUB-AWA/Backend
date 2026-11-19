package com.irion.irion_backend.domain.region.dto;

import java.util.List;

public record RegionListResponse(
        List<RegionResponse> regions
) {
}
