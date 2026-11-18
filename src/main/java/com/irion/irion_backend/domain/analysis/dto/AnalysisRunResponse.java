package com.irion.irion_backend.domain.analysis.dto;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;

public record AnalysisRunResponse(
        Long analysisRunId,
        AnalysisStatus status
) {

    public static AnalysisRunResponse from(AnalysisRun analysisRun) {
        return new AnalysisRunResponse(analysisRun.getId(), analysisRun.getStatus());
    }
}
