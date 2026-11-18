package com.irion.irion_backend.domain.aiConsultation.property.dto.response;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.property.entity.ImprovementSuggestion;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.property.entity.RiskFactor;


import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record PropertyConsultationDetailResponse(
        Long propertyConsultId,
        Long propertyAnalysisId,
        String summary,
        List<RiskFactorResponse> riskFactors,
        List<ImprovementSuggestionResponse> improvementSuggestions,
        String contractConsiderations,
        ConsultationStatus status,
        LocalDateTime completedAt
) {

    public static PropertyConsultationDetailResponse from(PropertyConsultation consultation) {
        boolean completed = consultation.getStatus() == ConsultationStatus.COMPLETED;

        return new PropertyConsultationDetailResponse(
                consultation.getId(),
                consultation.getPropertyAnalysis().getId(),
                completed ? consultation.getSummary() : null,
                completed ? toRiskFactors(consultation.getRiskFactors()) : null,
                completed ? toImprovementSuggestions(consultation.getImprovementSuggestions()) : null,
                completed ? consultation.getContractConsiderations() : null,
                consultation.getStatus(),
                consultation.getCompletedAt()
        );
    }

    private static List<RiskFactorResponse> toRiskFactors(List<RiskFactor> riskFactors) {
        if (riskFactors == null) {
            return List.of();
        }
        return riskFactors.stream().map(RiskFactorResponse::from).toList();
    }

    private static List<ImprovementSuggestionResponse> toImprovementSuggestions(List<ImprovementSuggestion> improvementSuggestions) {
        if (improvementSuggestions == null) {
            return List.of();
        }
        return improvementSuggestions.stream().map(ImprovementSuggestionResponse::from)
                .sorted(Comparator.comparing(ImprovementSuggestionResponse::priority, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }
}
