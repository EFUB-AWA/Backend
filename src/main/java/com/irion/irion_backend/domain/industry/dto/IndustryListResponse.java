package com.irion.irion_backend.domain.industry.dto;

import java.util.List;

public record IndustryListResponse(
        List<IndustryResponse> industries
) {
}