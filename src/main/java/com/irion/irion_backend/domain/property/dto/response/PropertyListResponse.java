package com.irion.irion_backend.domain.property.dto.response;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;

import java.util.List;

public record PropertyListResponse(
        List<PropertySummaryResponse> properties,
        int total
) {

    public static PropertyListResponse from(List<CandidateProperty> properties) {
        return new PropertyListResponse(
                properties.stream()
                        .map(PropertySummaryResponse::from)
                        .toList(),
                properties.size()
        );
    }
}
