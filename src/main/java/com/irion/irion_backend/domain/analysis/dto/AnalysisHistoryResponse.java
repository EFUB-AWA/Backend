package com.irion.irion_backend.domain.analysis.dto;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public record AnalysisHistoryResponse(List<AnalysisSummary> analyses) {
    public record PropertySummary(Long candidatePropertyId, String propertyName, String address, String safetyGrade) {}

    public record AnalysisSummary(Long analysisRunId, String title, AnalysisStatus status,
                                  LocalDateTime completedAt, List<PropertySummary> properties) {
        public static AnalysisSummary from(AnalysisRun run, List<PropertyAnalysis> rows) {
            var ids = run.getCandidatePropertyIds();
            var snapshots = AnalysisDetailResponse.indexedSnapshots(run, ids);
            boolean completed = switch (run.getStatus()) {
                case COMPLETED -> true;
                case PROCESSING, FAILED -> false;
            };
            if (!completed && !rows.isEmpty()) throw new IllegalStateException("Unexpected results for unfinished run");

            var byCandidate = new HashMap<Long, PropertyAnalysis>();
            for (var row : rows) {
                Long id = row.getCandidateProperty().getId();
                if (!snapshots.containsKey(id) || byCandidate.putIfAbsent(id, row) != null) {
                    throw new IllegalStateException("Unexpected or duplicate candidate result");
                }
            }
            var properties = new ArrayList<PropertySummary>(ids.size());
            for (Long id : ids) {
                var snapshot = snapshots.get(id);
                Object name = null;
                Object address = null;
                String grade = null;
                if (completed) {
                    var row = byCandidate.get(id);
                    if (row == null) throw new IllegalStateException("Missing candidate result");
                    var stored = AnalysisDetailResponse.resultSnapshot(row, id);
                    name = stored.get("name");
                    address = stored.get("address");
                    grade = AnalysisDetailResponse.grade(row.getSafetyGrade());
                }
                properties.add(new PropertySummary(id, AnalysisDetailResponse.snapshotText(name, snapshot.name()),
                        AnalysisDetailResponse.snapshotText(address, snapshot.address()), grade));
            }
            return new AnalysisSummary(run.getId(), run.getTitle(), run.getStatus(),
                    completed ? run.getCompletedAt() : null, List.copyOf(properties));
        }
    }
}
