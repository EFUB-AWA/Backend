package com.irion.irion_backend.domain.aiConsultation.property.dto.response;

import com.irion.irion_backend.domain.aiConsultation.property.entity.ImprovementSuggestion;

public record ImprovementSuggestionResponse(
        Integer priority,
        String content
) {

    public static ImprovementSuggestionResponse from(ImprovementSuggestion improvementSuggestion) {
        return new ImprovementSuggestionResponse(
                improvementSuggestion.priority(),
                improvementSuggestion.content()
        );
    }
}
