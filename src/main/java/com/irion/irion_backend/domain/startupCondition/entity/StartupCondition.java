
package com.irion.irion_backend.domain.startupCondition.entity;

import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "startup_conditions")
public class StartupCondition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "preferred_region_code", nullable = false)
    private String preferredRegionCode;

    @Column(name = "total_capital", nullable = false)
    private Long totalCapital;

    @Column(name = "available_capital", nullable = false)
    private Long availableCapital;

    @Column(name = "loan_amount", nullable = false)
    private Long loanAmount;

    @Column(name = "monthly_labor_cost", nullable = false)
    private Long monthlyLaborCost;

    @Column(name = "cost_of_sales_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal costOfSalesRate;

    @Column(name = "monthly_other_operating_cost", nullable = false)
    private Long monthlyOtherOperatingCost;

    @Builder
    private StartupCondition(
            User user,
            Industry industry,
            String title,
            String preferredRegionCode,
            Long totalCapital,
            Long availableCapital,
            Long loanAmount,
            Long monthlyLaborCost,
            BigDecimal costOfSalesRate,
            Long monthlyOtherOperatingCost
    ) {
        this.user = user;
        this.industry = industry;
        this.title = title;
        this.preferredRegionCode = preferredRegionCode;
        this.totalCapital = totalCapital;
        this.availableCapital = availableCapital;
        this.loanAmount = loanAmount;
        this.monthlyLaborCost = monthlyLaborCost;
        this.costOfSalesRate = costOfSalesRate;
        this.monthlyOtherOperatingCost = monthlyOtherOperatingCost;
    }

    // 창업 조건 수정
    public void update(
            Industry industry,
            String title,
            String preferredRegionCode,
            Long totalCapital,
            Long availableCapital,
            Long loanAmount,
            Long monthlyLaborCost,
            BigDecimal costOfSalesRate,
            Long monthlyOtherOperatingCost
    ) {
        this.industry = industry;
        this.title = title;
        this.preferredRegionCode = preferredRegionCode;
        this.totalCapital = totalCapital;
        this.availableCapital = availableCapital;
        this.loanAmount = loanAmount;
        this.monthlyLaborCost = monthlyLaborCost;
        this.costOfSalesRate = costOfSalesRate;
        this.monthlyOtherOperatingCost = monthlyOtherOperatingCost;
    }
}
