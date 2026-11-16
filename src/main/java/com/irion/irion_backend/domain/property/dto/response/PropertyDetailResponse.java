package com.irion.irion_backend.domain.property.dto.response;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.enums.MappingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PropertyDetailResponse(
        Long propertyId,
        Long startupConditionId,
        String name,
        String address,
        String addressDetail,
        Double latitude,
        Double longitude,
        BigDecimal areaM2,
        Long deposit,
        Long monthlyRent,
        Long keyMoney,
        Long monthlyManagementFee,
        MappingStatus mappingStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PropertyDetailResponse from(CandidateProperty property) {
        return new PropertyDetailResponse(
                property.getId(),
                property.getStartupCondition().getId(),
                property.getName(),
                property.getAddress(),
                property.getAddressDetail(),
                property.getLatitude(),
                property.getLongitude(),
                property.getAreaM2(),
                property.getDeposit(),
                property.getMonthlyRent(),
                property.getKeyMoney(),
                property.getMonthlyManagementFee(),
                property.getMappingStatus(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}
