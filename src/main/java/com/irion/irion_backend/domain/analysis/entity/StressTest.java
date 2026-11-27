package com.irion.irion_backend.domain.analysis.entity;

import com.irion.irion_backend.domain.analysis.dto.StressTestResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stress_tests", uniqueConstraints = @UniqueConstraint(columnNames = {"property_analysis_id", "sales_decrease_percent"}))
public class StressTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_analysis_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PropertyAnalysis propertyAnalysis;

    @Column(name = "sales_decrease_percent", nullable = false)
    private int salesDecreasePercent;

    @Column(name = "safety_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal safetyScore;

    @Column(name = "safety_grade", nullable = false)
    private String safetyGrade;

    @Column(name = "depletion_status", nullable = false)
    private String depletionStatus;

    @Column(name = "depletion_month_index")
    private Integer depletionMonthIndex;

    public StressTest(PropertyAnalysis propertyAnalysis, StressTestResult result) {
        this.propertyAnalysis = propertyAnalysis;
        this.salesDecreasePercent = result.salesDecreasePercent();
        this.safetyScore = result.safetyScore();
        this.safetyGrade = result.safetyGrade();
        this.depletionStatus = result.depletionMonthIndex() == null ? "NOT_DEPLETED" : "DEPLETED";
        this.depletionMonthIndex = result.depletionMonthIndex();
    }
}
