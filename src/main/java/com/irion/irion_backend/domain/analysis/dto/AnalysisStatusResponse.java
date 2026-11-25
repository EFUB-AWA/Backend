package com.irion.irion_backend.domain.analysis.dto;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;

import java.time.LocalDateTime;

public record AnalysisStatusResponse(Long analysisRunId, AnalysisStatus status, LocalDateTime completedAt) {
    public static AnalysisStatusResponse from(AnalysisRun analysisRun) {
        var status = analysisRun.getStatus();
        return new AnalysisStatusResponse(analysisRun.getId(), status,
                status == AnalysisStatus.COMPLETED ? analysisRun.getCompletedAt() : null);
    }
}
