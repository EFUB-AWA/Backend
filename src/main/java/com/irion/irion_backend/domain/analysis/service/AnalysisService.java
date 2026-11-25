package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisDetailResponse;
import com.irion.irion_backend.domain.analysis.dto.AnalysisHistoryResponse;
import com.irion.irion_backend.domain.user.repository.UserRepository;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunResponse;
import com.irion.irion_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.domain.analysis.repository.PropertyAnalysisRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.irion.irion_backend.domain.user.exception.UserErrorCode.USER_NOT_FOUND;
import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.ANALYSIS_ACCESS_DENIED;
import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.ANALYSIS_NOT_FOUND;

import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.ANALYSIS_NOT_COMPLETED;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private final AnalysisRequestValidator validator;
    private final AnalysisSnapshotFactory snapshotFactory;
    private final AnalysisRunRepository repository;
    private final ApplicationEventPublisher events;
    private final PropertyAnalysisRepository properties;

    private final UserRepository users;

    public record AnalysisStarted(Long runId) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalysisHistoryResponse getHistory(Long requesterId) {
        if (!users.existsById(requesterId)) throw new BusinessException(USER_NOT_FOUND);
        var runs = repository.findByStartupCondition_User_IdOrderByCreatedAtDescIdDesc(requesterId);
        if (runs.isEmpty()) return new AnalysisHistoryResponse(List.of());

        var ids = runs.stream().map(AnalysisRun::getId).toList();
        var byRun = properties.findByAnalysisRun_IdIn(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getAnalysisRun().getId()));
        return new AnalysisHistoryResponse(runs.stream()
                .map(run -> AnalysisHistoryResponse.AnalysisSummary.from(run, byRun.getOrDefault(run.getId(), List.of())))
                .toList());
    }

    @Transactional(readOnly = true)
    public AnalysisDetailResponse getDetail(Long requesterId, Long analysisRunId) {
        var run = ownedRun(requesterId, analysisRunId);
        if (run.getStatus() != AnalysisStatus.COMPLETED) throw new BusinessException(ANALYSIS_NOT_COMPLETED);
        return AnalysisDetailResponse.from(run, properties.findByAnalysisRun_Id(analysisRunId));
    }

    @Transactional(readOnly = true)
    public AnalysisStatusResponse getStatus(Long requesterId, Long analysisRunId) {
        return AnalysisStatusResponse.from(ownedRun(requesterId, analysisRunId));
    }

    private AnalysisRun ownedRun(Long requesterId, Long analysisRunId) {
        var run = repository.findById(analysisRunId)
                .orElseThrow(() -> new BusinessException(ANALYSIS_NOT_FOUND));
        if (requesterId == null || !requesterId.equals(run.getStartupCondition().getUser().getId())) {
            throw new BusinessException(ANALYSIS_ACCESS_DENIED);
        }
        return run;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AnalysisRunResponse start(Long requesterId, AnalysisRunRequest request) {
        var validated = validator.validate(requesterId, request);
        var condition = validated.startupCondition();
        var conditionSnapshot = snapshotFactory.conditionSnapshot(condition);
        var snapshots = snapshotFactory.propertySnapshots(validated.candidateProperties());
        var ids = snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList();
        var run = AnalysisRun.create(condition, request.title(), ids, conditionSnapshot, snapshots);
        var saved = repository.save(run);
        events.publishEvent(new AnalysisStarted(saved.getId()));
        return AnalysisRunResponse.from(saved);
    }
}
