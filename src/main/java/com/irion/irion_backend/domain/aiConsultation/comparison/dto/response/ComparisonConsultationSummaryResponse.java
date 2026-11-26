package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record ComparisonConsultationSummaryResponse(
        Long comparisonConsultId,
        Long recommendedPropertyAnalysisId,
        List<ComparedPropertyResponse> properties,
        LocalDateTime completedAt, LocalDateTime savedAt
) {

    public static ComparisonConsultationSummaryResponse from(ComparisonConsultation consultation) {
        return new ComparisonConsultationSummaryResponse(
                consultation.getId(),
                consultation.getRecommendedPropertyAnalysisId(),
                toProperties(consultation.getPropertySnapshots()),
                consultation.getCompletedAt(), consultation.getSavedAt()
        );
    }

    private static List<ComparedPropertyResponse> toProperties(List<PropertySnapshot> propertySnapshots) {
        if (propertySnapshots == null) {
            return List.of();
        }
        return propertySnapshots.stream().map(ComparedPropertyResponse::from).toList();
    }
}
