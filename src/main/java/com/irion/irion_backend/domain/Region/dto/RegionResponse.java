package com.irion.irion_backend.domain.Region.dto;

import com.irion.irion_backend.domain.Region.entity.Region;

public record RegionResponse(
        String code,
        String name
) {
    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getCode(),
                region.getName()
        );
    }
}
