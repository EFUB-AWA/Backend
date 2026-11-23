package com.irion.irion_backend.domain.aiConsultation.property.entity;

import com.irion.irion_backend.domain.aiConsultation.property.enums.RiskLevel;

public record RiskFactor(
        RiskLevel level,
        String content
) {
}
