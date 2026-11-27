package com.irion.irion_backend.domain.aiConsultation.comparison.service;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.repository.ComparisonConsultationRepository;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.domain.analysis.repository.PropertyAnalysisRepository;
import com.irion.irion_backend.domain.user.repository.UserRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ComparisonConsultationTransactions {
    private final ComparisonConsultationRepository comparisons;
    private final PropertyAnalysisRepository properties;
    private final AnalysisRunRepository runs;
    private final UserRepository users;

    record Reservation(ComparisonConsultationDetailResponse detail, List<PropertySnapshot> input, long attempt) {}
    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }

    public Optional<Reservation> reusable(Long owner, String key) {
        return comparisons.reusable(owner, key, now())
                .map(c -> new Reservation(ComparisonConsultationDetailResponse.from(c), null, c.getAttemptNo()));
    }

    public Reservation reserve(Long owner, List<Long> ids, String key) {
        var existing = reusable(owner, key);
        if (existing.isPresent()) return existing.get();

        // The first read is scalar: no stale analysis entity can survive the wait for a run lock.
        for (long runId : properties.findSourceRunIds(ids)) runs.findForUpdate(runId);
        existing = reusable(owner, key);
        if (existing.isPresent()) return existing.get();
        if (comparisons.removeExpiredKey(owner, key, now()) == 0) {
            // A concurrent save can make an expired row reusable while the delete waits.
            existing = reusable(owner, key);
            if (existing.isPresent()) return existing.get();
        }
        var rows = properties.findComparisonSources(ids).stream()
                .collect(Collectors.toMap(PropertyAnalysis::getId, Function.identity()));
        if (rows.size() != ids.size()) throw new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND);
        for (var row : rows.values()) {
            if (!owner.equals(row.getAnalysisRun().getStartupCondition().getUser().getId())
                    || !owner.equals(row.getCandidateProperty().getStartupCondition().getUser().getId()))
                throw new BusinessException(CONSULTATION_ACCESS_DENIED);
        }
        if (rows.values().stream().anyMatch(p -> p.getAnalysisRun().getStatus() != AnalysisStatus.COMPLETED))
            throw new BusinessException(ANALYSIS_NOT_READY);
        if (rows.values().stream().map(p -> p.getCandidateProperty().getId()).distinct().count() != ids.size())
            throw new BusinessException(INVALID_COMPARISON_SELECTION);

        var snapshots = ids.stream().map(rows::get).map(PropertySnapshot::capture).toList();
        var pending = comparisons.saveAndFlush(ComparisonConsultation.pending(users.getReferenceById(owner), key, snapshots, now()));
        return new Reservation(ComparisonConsultationDetailResponse.from(pending), snapshots, pending.getAttemptNo());
    }

    public Reservation reserveRetry(Long owner, Long id) {
        var c = comparisons.lockById(id).filter(row -> row.getUser().getId().equals(owner))
                .orElseThrow(() -> new BusinessException(CONSULTATION_NOT_FOUND));
        c.beginRetry(now());
        var snapshots = c.getPropertySnapshots();
        if (snapshots == null || snapshots.size() < 2 || snapshots.size() > 3)
            throw new BusinessException(ANALYSIS_NOT_READY);
        var analysisIds = new HashSet<Long>();
        var candidateIds = new HashSet<Long>();
        for (var snapshot : snapshots) {
            if (snapshot == null || !analysisIds.add(snapshot.propertyAnalysisId()) || !candidateIds.add(snapshot.propertyId()))
                throw new BusinessException(ANALYSIS_NOT_READY);
            snapshot.requireRetryInput();
        }
        return new Reservation(ComparisonConsultationDetailResponse.from(c), snapshots, c.getAttemptNo());
    }

    public ComparisonConsultationDetailResponse complete(Long id, long attempt, ComparisonConsultation.Report report) {
        var c = comparisons.lockById(id).orElseThrow(() -> new BusinessException(CONSULTATION_NOT_PENDING));
        c.complete(attempt, report, now());
        return ComparisonConsultationDetailResponse.from(c);
    }

    public void fail(Long id, long attempt) {
        comparisons.lockById(id).orElseThrow(() -> new BusinessException(CONSULTATION_NOT_PENDING)).fail(attempt, now());
    }
}
