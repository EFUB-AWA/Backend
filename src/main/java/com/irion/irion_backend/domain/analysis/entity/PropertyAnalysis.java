package com.irion.irion_backend.domain.analysis.entity;

import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "property_analyses")
public class PropertyAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_property_id", nullable = false)
    private CandidateProperty candidateProperty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "property_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> propertySnapshot;

    @Column(name = "expected_monthly_sales")
    private Long expectedMonthlySales;

    @Column(name = "monthly_fixed_cost")
    private Long monthlyFixedCost;

    @Column(name = "variable_cost_rate", columnDefinition = "numeric")
    private BigDecimal variableCostRate;

    @Column(name = "monthly_operating_profit")
    private Long monthlyOperatingProfit;

    @Column(name = "break_even_sales")
    private Long breakEvenSales;

    @Column(name = "break_even_status")
    private String breakEvenStatus;

    @Column(name = "initial_required_cash")
    private Long initialRequiredCash;

    @Column(name = "initial_operating_cash")
    private Long initialOperatingCash;

    @Column(name = "safety_score", columnDefinition = "numeric")
    private BigDecimal safetyScore;

    @Column(name = "safety_grade")
    private String safetyGrade;

    @Column(name = "recommendation_rank")
    private Integer recommendationRank;

    @Column(name = "recommendation_reason", columnDefinition = "text")
    private String recommendationReason;

    public static PropertyAnalysis from(AnalysisRun run, CandidateProperty property, Map<String, Object> snapshot,
                                        CalculatedPropertyResult result) {
        result.requireComplete();
        PropertyAnalysis row = new PropertyAnalysis();
        row.analysisRun = run;
        row.candidateProperty = property;
        row.propertySnapshot = snapshot;
        row.expectedMonthlySales = result.expectedMonthlySales();
        row.monthlyFixedCost = result.monthlyFixedCost();
        row.variableCostRate = result.variableCostRate();
        row.monthlyOperatingProfit = result.monthlyOperatingProfit();
        row.breakEvenSales = result.breakEvenSales();
        row.breakEvenStatus = result.breakEvenStatus();
        row.initialRequiredCash = result.initialRequiredCash();
        row.initialOperatingCash = result.initialOperatingCash();
        row.safetyScore = result.safetyScore();
        row.safetyGrade = result.safetyGrade();
        row.recommendationRank = result.recommendationRank();
        row.recommendationReason = result.recommendationReason();
        return row;
    }
}
