package com.irion.irion_backend.domain.aiConsultation.property.entity;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
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
@Table(name = "property_ai_consultations")
public class PropertyConsultation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_analysis_id", nullable = false, unique = true)
    private PropertyAnalysis propertyAnalysis;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConsultationStatus status;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "risk_factors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RiskFactor> riskFactors;

    @Column(name = "improvement_suggestions", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ImprovementSuggestion> improvementSuggestions;

    @Column(name = "contract_considerations", columnDefinition = "text")
    private String contractConsiderations;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


}
