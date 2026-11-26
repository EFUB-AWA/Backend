package com.irion.irion_backend.domain.aiConsultation.comparison.entity;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comparison_ai_consultations")
public class ComparisonConsultation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_run_id", nullable = false, unique = true, updatable = false)
    private Long analysisRunId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "property_snapshots", columnDefinition = "jsonb", nullable = false)
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


    public boolean isExpired(LocalDateTime now) {
        if (!this.saved && this.expiresAt != null) {
            return this.expiresAt.isBefore(now);
        }
        return false;
    }
}
