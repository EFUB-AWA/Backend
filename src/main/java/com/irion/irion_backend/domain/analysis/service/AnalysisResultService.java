package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.domain.analysis.repository.PropertyAnalysisRepository;
import com.irion.irion_backend.domain.property.repository.CandidatePropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisResultService {
    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};
    private final AnalysisRunRepository runs;
    private final PropertyAnalysisRepository analyses;
    private final CandidatePropertyRepository properties;
    private final ObjectMapper mapper;
    private final AnalysisFailureService failures;
    private final TransactionTemplate resultTransaction;

    public AnalysisResultService(AnalysisRunRepository runs, PropertyAnalysisRepository analyses,
                                 CandidatePropertyRepository properties, ObjectMapper mapper,
                                 AnalysisFailureService failures, PlatformTransactionManager transactions) {
        this.runs = runs;
        this.analyses = analyses;
        this.properties = properties;
        this.mapper = mapper;
        this.failures = failures;
        this.resultTransaction = new TransactionTemplate(transactions);
        this.resultTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> freezeStatistics(Long runId, Map<String, Object> observation) {
        var run = runs.findForUpdate(runId).orElseThrow();
        run.addConditionObservation(AnalysisCommercialStatistics.SNAPSHOT_KEY, observation);
        return Map.copyOf(run.getConditionSnapshot());
    }

    public void complete(Long runId, List<CalculatedPropertyResult> results, Map<String, Object> assumptions,
                         String calculationVersion, LocalDateTime dataCutoffAt, Integer horizonMonths) {
        try {
            resultTransaction.executeWithoutResult(transaction -> {
                var run = runs.findForUpdate(runId).orElseThrow();
                if (run.getStatus() != AnalysisStatus.PROCESSING) {
                    return;
                }
                var snapshots = run.getPropertySnapshots();
                var ids = snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList();
                requireMatchingIds(run.getCandidatePropertyIds(), ids,
                        results.stream().map(CalculatedPropertyResult::candidatePropertyId).toList());
                for (int i = 0; i < snapshots.size(); i++) {
                    var snapshot = snapshots.get(i);
                    var property = properties.getReferenceById(snapshot.candidatePropertyId());
                    analyses.save(PropertyAnalysis.from(run, property, mapper.convertValue(snapshot, SNAPSHOT_TYPE),
                            results.get(i)));
                }
                run.complete(assumptions, calculationVersion, dataCutoffAt, horizonMonths);
            });
        } catch (RuntimeException cause) {
            try {
                failures.fail(runId, cause);
            } catch (RuntimeException recordingFailure) {
                cause.addSuppressed(recordingFailure);
            }
            throw cause;
        }
    }

    static void requireMatchingIds(List<Long> requested, List<Long> snapshots, List<Long> results) {
        if (requested.isEmpty() || !requested.equals(snapshots) || !snapshots.equals(results)) {
            throw new IllegalArgumentException("Results must match every requested property in order");
        }
        if (new HashSet<>(requested).size() != requested.size()) {
            throw new IllegalArgumentException("Duplicate property IDs cannot identify individual result rows");
        }
    }
}
