package com.irion.irion_backend.domain.industry.dto;

import com.irion.irion_backend.domain.industry.entity.Industry;

public record IndustryResponse(
        Long industryId,
        String code,
        String category,
        String name
) {
    public static IndustryResponse from(Industry industry) {
        return new IndustryResponse(
                industry.getId(),
                industry.getCode(),
                industry.getCategory(),
                industry.getName()
        );
    }
}
