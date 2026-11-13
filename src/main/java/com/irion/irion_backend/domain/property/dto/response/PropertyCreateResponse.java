package com.irion.irion_backend.domain.property.dto.response;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.enums.MappingStatus;

import java.time.LocalDateTime;

public record PropertyCreateResponse(
        Long propertyId,
        MappingStatus mappingStatus,
        LocalDateTime createdAt
) {

    public static PropertyCreateResponse from(CandidateProperty property) {
        return new PropertyCreateResponse(
                property.getId(),
                property.getMappingStatus(),
                property.getCreatedAt());
    }
}
