package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;

import java.time.LocalDateTime;

public record ComparisonConsultationSaveResponse(
        Long comparisonConsultId,
        LocalDateTime savedAt
) {

    public static ComparisonConsultationSaveResponse from(ComparisonConsultation consultation) {
        return new ComparisonConsultationSaveResponse(
                consultation.getId(),
                consultation.getSavedAt()
        );
    }

    public static ComparisonConsultationSaveResponse of(Long consultationId, LocalDateTime now) {
        return new ComparisonConsultationSaveResponse(consultationId, now);
    }
}
