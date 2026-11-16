package com.irion.irion_backend.domain.costbenchmark.entity;

import com.irion.irion_backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cost_benchmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cost_benchmark_industry_region_year",
                        columnNames = {"kosis_industry_code", "region_code", "reference_year"}
                )
        }
)
public class CostBenchmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kosis_industry_code", nullable = false, length = 30)
    private String kosisIndustryCode;

    @Column(name = "kosis_industry_name", nullable = false, length = 100)
    private String kosisIndustryName;

    @Column(name = "region_code", nullable = false, length = 30)
    private String regionCode;

    @Column(name = "reference_year", nullable = false)
    private Integer referenceYear;

    @Column(name = "monthly_labor_cost", nullable = false)
    private Long monthlyLaborCost;

    @Column(
            name = "cost_of_sales_rate",
            nullable = false,
            precision = 7,
            scale = 2
    )
    private BigDecimal costOfSalesRate;

    @Column(name = "monthly_other_operating_cost", nullable = false)
    private Long monthlyOtherOperatingCost;


    public static CostBenchmark create(
            String kosisIndustryCode,
            String kosisIndustryName,
            String regionCode,
            Integer referenceYear,
            Long monthlyLaborCost,
            BigDecimal costOfSalesRate,
            Long monthlyOtherOperatingCost
    ) {
        CostBenchmark benchmark = new CostBenchmark();

        benchmark.kosisIndustryCode = kosisIndustryCode;
        benchmark.kosisIndustryName = kosisIndustryName;
        benchmark.regionCode = regionCode;
        benchmark.referenceYear = referenceYear;
        benchmark.monthlyLaborCost = monthlyLaborCost;
        benchmark.costOfSalesRate = costOfSalesRate;
        benchmark.monthlyOtherOperatingCost = monthlyOtherOperatingCost;

        return benchmark;
    }

    public void updateCosts(
            String kosisIndustryName,
            Long monthlyLaborCost,
            BigDecimal costOfSalesRate,
            Long monthlyOtherOperatingCost
    ) {
        this.kosisIndustryName = kosisIndustryName;
        this.monthlyLaborCost = monthlyLaborCost;
        this.costOfSalesRate = costOfSalesRate;
        this.monthlyOtherOperatingCost = monthlyOtherOperatingCost;
    }

}


