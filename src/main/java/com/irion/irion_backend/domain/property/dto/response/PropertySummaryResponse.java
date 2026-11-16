package com.irion.irion_backend.domain.property.dto.response;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.enums.MappingStatus;

import java.time.LocalDateTime;

public record PropertySummaryResponse(
        Long propertyId,
        String name,
        String address,
        String addressDetail,
        MappingStatus mappingStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PropertySummaryResponse from(CandidateProperty property) {
        return new PropertySummaryResponse(
                property.getId(),
                property.getName(),
                property.getAddress(),
                property.getAddressDetail(),
                property.getMappingStatus(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}
