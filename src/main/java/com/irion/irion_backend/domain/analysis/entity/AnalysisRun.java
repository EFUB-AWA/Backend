package com.irion.irion_backend.domain.analysis.entity;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_runs")
public class AnalysisRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "startup_condition_id", nullable = false)
    private StartupCondition startupCondition;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_property_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<Long> candidatePropertyIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "property_snapshots", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<PropertySnapshotV1> propertySnapshots;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditionSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assumptions_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> assumptionsSnapshot;

    @Column(name = "calculation_version")
    private String calculationVersion;

    @Column(name = "data_cutoff_at")
    private LocalDateTime dataCutoffAt;

    @Column(name = "horizon_months")
    private Integer horizonMonths;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static AnalysisRun create(StartupCondition startupCondition, String title,
                                     List<Long> candidatePropertyIds, Map<String, Object> conditionSnapshot,
                                     List<PropertySnapshotV1> propertySnapshots) {
        Objects.requireNonNull(startupCondition, "startupCondition is required");
        Objects.requireNonNull(candidatePropertyIds, "candidatePropertyIds is required");
        Objects.requireNonNull(conditionSnapshot, "conditionSnapshot is required");
        List<Long> ids = List.copyOf(candidatePropertyIds);
        List<PropertySnapshotV1> snapshots = List.copyOf(propertySnapshots);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("candidatePropertyIds must not be empty");
        }

        if (!ids.equals(snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList())) {
            throw new IllegalArgumentException("propertySnapshots must match candidatePropertyIds in request order");
        }

        AnalysisRun run = new AnalysisRun();
        run.startupCondition = startupCondition;
        run.title = title;
        run.candidatePropertyIds = ids;
        run.propertySnapshots = snapshots;
        run.conditionSnapshot = Map.copyOf(conditionSnapshot);
        run.status = AnalysisStatus.PROCESSING;
        return run;
    }

    public List<PropertySnapshotV1> getPropertySnapshots() {
        return List.copyOf(propertySnapshots);
    }

    public List<Long> getCandidatePropertyIds() {
        return List.copyOf(candidatePropertyIds);
    }

    public void addConditionObservation(String key, Map<String, Object> observation) {
        requireProcessing();
        var updated = new HashMap<>(conditionSnapshot);
        updated.putIfAbsent(key, Map.copyOf(observation));
        conditionSnapshot = Map.copyOf(updated);
    }

    public void complete(Map<String, Object> assumptionsSnapshot, String calculationVersion,
                         LocalDateTime dataCutoffAt, Integer horizonMonths) {
        requireProcessing();
        Objects.requireNonNull(assumptionsSnapshot, "assumptionsSnapshot is required");
        Objects.requireNonNull(calculationVersion, "calculationVersion is required");
        Objects.requireNonNull(dataCutoffAt, "dataCutoffAt is required");
        Objects.requireNonNull(horizonMonths, "horizonMonths is required");

        this.assumptionsSnapshot = Map.copyOf(assumptionsSnapshot);
        this.calculationVersion = calculationVersion;
        this.dataCutoffAt = dataCutoffAt;
        this.horizonMonths = horizonMonths;
        this.completedAt = LocalDateTime.now();
        this.status = AnalysisStatus.COMPLETED;
    }

    public void fail() {
        requireProcessing();
        this.status = AnalysisStatus.FAILED;
    }

    private void requireProcessing() {
        if (status != AnalysisStatus.PROCESSING) {
            throw new IllegalStateException("AnalysisRun must be PROCESSING");
        }
    }
}