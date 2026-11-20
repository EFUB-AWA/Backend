package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;

import java.util.List;

public record ComparisonConsultationListResponse(
        List<ComparisonConsultationSummaryResponse> consultations,
        boolean hasNext,
        Long nextCursor
) {

    // fetched는 size + 1까지 조회한 결과, size는 1 이상
    public static ComparisonConsultationListResponse of(List<ComparisonConsultation> fetched,
                                                 int size) {
        boolean hasNext = fetched.size() > size;
        List<ComparisonConsultation> content = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? content.get(content.size() - 1).getId() : null;

        return new ComparisonConsultationListResponse(
                content.stream().map(ComparisonConsultationSummaryResponse::from).toList(),
                hasNext,
                nextCursor
        );
    }
}
