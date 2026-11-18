package com.irion.irion_backend.domain.aiConsultation.property.dto.response;

import com.irion.irion_backend.domain.aiConsultation.property.entity.RiskFactor;
import com.irion.irion_backend.domain.aiConsultation.property.enums.RiskLevel;

public record RiskFactorResponse(
        RiskLevel level,
        String content
) {

    public static RiskFactorResponse from(RiskFactor riskFactor) {
        return new RiskFactorResponse(
                riskFactor.level(),
                riskFactor.content()
        );
    }
}
