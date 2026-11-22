package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunResponse;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private final AnalysisRequestValidator validator;
    private final AnalysisSnapshotFactory snapshotFactory;
    private final AnalysisRunRepository repository;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AnalysisRunResponse start(Long requesterId, AnalysisRunRequest request) {
        var validated = validator.validate(requesterId, request);
        var condition = validated.startupCondition();
        var conditionSnapshot = snapshotFactory.conditionSnapshot(condition);
        var snapshots = snapshotFactory.propertySnapshots(validated.candidateProperties());
        var ids = snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList();
        var run = AnalysisRun.create(condition, request.title(), ids, conditionSnapshot, snapshots);
        return AnalysisRunResponse.from(repository.save(run));
    }
}
