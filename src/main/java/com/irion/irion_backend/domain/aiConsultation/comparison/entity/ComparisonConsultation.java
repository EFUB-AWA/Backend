package com.irion.irion_backend.domain.aiConsultation.comparison.entity;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import com.irion.irion_backend.global.error.BusinessException;
import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comparison_ai_consultations", uniqueConstraints = @UniqueConstraint(name = "uq_comparison_user_key", columnNames = {"user_id","comparison_key"}))
public class ComparisonConsultation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comparison_key", length = 64, updatable = false)
    private String comparisonKey;

    @Column(name = "attempt_no", nullable = false)
    @ColumnDefault("1")
    private long attemptNo;

    @Column(name = "attempt_started_at", nullable = false)
    @ColumnDefault("current_timestamp")
    private LocalDateTime attemptStartedAt;

    private Long recommendedPropertyAnalysisId;
    @Column(columnDefinition = "text")
    private String conditionDifferences;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> riskFactors;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> positiveFactors;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> recommendedContractConditions;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "property_snapshots", columnDefinition = "jsonb", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<PropertySnapshot> propertySnapshots;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsultationStatus status;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "pros_and_cons", columnDefinition = "text")
    private String prosAndCons;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "is_saved", nullable = false)
    private boolean saved;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;


    public static ComparisonConsultation pending(User user, String key, List<PropertySnapshot> snapshots, LocalDateTime now) {
        if (key == null || key.isBlank() || snapshots.size() < 2 || snapshots.size() > 3)
            throw new IllegalArgumentException("Invalid comparison reservation");
        var c = new ComparisonConsultation();
        c.user = user;
        c.comparisonKey = key;
        c.propertySnapshots = List.copyOf(snapshots);
        c.attemptNo = 1;
        c.attemptStartedAt = now;
        c.status = ConsultationStatus.PENDING;
        return c;
    }

    public void beginRetry(LocalDateTime now) {
        if (status != ConsultationStatus.FAILED) throw new BusinessException(CONSULTATION_NOT_RETRYABLE);
        if (isExpired(now)) throw new BusinessException(CONSULTATION_EXPIRED);
        attemptNo++;
        attemptStartedAt = now;
        status = ConsultationStatus.PENDING;
        expiresAt = null;
        completedAt = null;
        summary = null;
        description = null;
        prosAndCons = null;
        conditionDifferences = null;
        modelName = null;
        promptVersion = null;
        riskFactors = null;
        positiveFactors = null;
        recommendedContractConditions = null;
        recommendedPropertyAnalysisId = null;
    }

    public void complete(long attempt, Report report, LocalDateTime now) {
        requirePending(attempt);
        recommendedPropertyAnalysisId = report.recommendedPropertyAnalysisId();
        summary = report.summary();
        description = report.description();
        riskFactors = report.riskFactors();
        positiveFactors = report.positiveFactors();
        recommendedContractConditions = report.recommendedContractConditions();
        conditionDifferences = report.conditionDifferences();
        modelName = report.modelName();
        promptVersion = report.promptVersion();
        completedAt = now;
        expiresAt = now.plusHours(24);
        status = ConsultationStatus.COMPLETED;
    }

    public record Report(Long recommendedPropertyAnalysisId, String summary, String description, List<String> riskFactors,
            List<String> positiveFactors, List<String> recommendedContractConditions, String conditionDifferences,
            String modelName, String promptVersion) {}

    public void fail(long attempt, LocalDateTime now) {
        requirePending(attempt);
        status = ConsultationStatus.FAILED;
        expiresAt = now.plusHours(24);
    }

    private void requirePending(long attempt) {
        if (status != ConsultationStatus.PENDING || attemptNo != attempt) throw new BusinessException(CONSULTATION_NOT_PENDING);
    }

    public boolean isExpired(LocalDateTime now) {
        if (!this.saved && status != ConsultationStatus.PENDING && this.expiresAt != null) {
            return !now.isBefore(this.expiresAt);
        }
        return false;
    }
}
