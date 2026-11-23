package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;

public record ComparedPropertyResponse(
        Long propertyId,
        String name
) {

    public static ComparedPropertyResponse from(PropertySnapshot snapshot) {
        return new ComparedPropertyResponse(
                snapshot.propertyId(),
                snapshot.name()
        );
    }
}
