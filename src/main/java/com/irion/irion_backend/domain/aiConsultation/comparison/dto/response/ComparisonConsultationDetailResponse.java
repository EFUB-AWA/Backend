package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import java.time.LocalDateTime;
import java.util.List;

public record ComparisonConsultationDetailResponse(Long comparisonConsultId, ConsultationStatus status,
        @JsonProperty("isSaved") boolean saved, Long recommendedPropertyAnalysisId, List<Property> properties,
        String summary, String description, String prosAndCons, List<String> riskFactors, List<String> positiveFactors,
        List<String> recommendedContractConditions, String conditionDifferences, LocalDateTime completedAt, LocalDateTime savedAt) {
    public record Property(Long propertyAnalysisId, Long analysisRunId, Long candidatePropertyId, String name, String address,
            LocalDateTime analyzedAt, PropertySnapshot.FinancialResult financialResult) {
        static Property from(PropertySnapshot s) {
            return new Property(s.propertyAnalysisId(), s.analysisRunId(), s.propertyId(), s.name(), s.address(), s.analyzedAt(), s.financialResult());
        }
    }

    public static ComparisonConsultationDetailResponse from(ComparisonConsultation c) {
        boolean done = c.getStatus() == ConsultationStatus.COMPLETED;
        return new ComparisonConsultationDetailResponse(c.getId(), c.getStatus(), c.isSaved(),
                done ? c.getRecommendedPropertyAnalysisId() : null, c.getPropertySnapshots().stream().map(Property::from).toList(),
                done ? c.getSummary() : null, done ? c.getDescription() : null, done ? c.getProsAndCons() : null,
                done ? c.getRiskFactors() : null, done ? c.getPositiveFactors() : null, done ? c.getRecommendedContractConditions() : null,
                done ? c.getConditionDifferences() : null, c.getCompletedAt(), c.getSavedAt());
    }
}
