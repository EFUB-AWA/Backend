package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;

public record ComparedPropertyResponse(Long propertyAnalysisId, Long analysisRunId, Long candidatePropertyId, String name) {
    static ComparedPropertyResponse from(PropertySnapshot s) {
        return new ComparedPropertyResponse(s.propertyAnalysisId(), s.analysisRunId(), s.propertyId(), s.name());
    }
}
