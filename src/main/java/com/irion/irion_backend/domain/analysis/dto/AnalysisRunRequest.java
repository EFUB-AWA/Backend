package com.irion.irion_backend.domain.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnalysisRunRequest(
        @NotNull(message = "창업 조건 ID는 필수입니다.")
        Long startupConditionId,

        @NotNull(message = "분석 대상 매물 목록은 필수입니다.")
        @Size(min = 1, message = "분석 대상 매물을 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "분석 대상 매물 ID는 null일 수 없습니다.") Long> candidatePropertyIds,

        String title
) {
}
