package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record ComparisonConsultationDetailResponse(
        Long comparisonConsultId,
        Long analysisRunId,
        List<ComparedPropertyResponse> properties,
        String summary,
        String description,
        String prosAndCons,
        ConsultationStatus status,
        LocalDateTime completedAt,
        LocalDateTime savedAt
) {

    public static ComparisonConsultationDetailResponse from(ComparisonConsultation consultation) {
        boolean completed = consultation.getStatus() == ConsultationStatus.COMPLETED;

        return new ComparisonConsultationDetailResponse(
                consultation.getId(),
                consultation.getAnalysisRunId(),
                toProperties(consultation.getPropertySnapshots()),
                completed ? consultation.getSummary() : null,
                completed ? consultation.getDescription() : null,
                completed ? consultation.getProsAndCons() : null,
                consultation.getStatus(),
                consultation.getCompletedAt(),
                consultation.getSavedAt()
        );
    }

    private static List<ComparedPropertyResponse> toProperties(List<PropertySnapshot> propertySnapshots) {
        if (propertySnapshots == null) {
            return List.of();
        }
        return propertySnapshots.stream().map(ComparedPropertyResponse::from).toList();
    }
}
