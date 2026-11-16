package com.irion.irion_backend.domain.costbenchmark.entity;

import com.irion.irion_backend.domain.industry.entity.Industry;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "industry_cost_benchmark_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_industry_cost_benchmark_mapping_industry",
                        columnNames = "industry_id"
                )
        }
)
public class IndustryCostBenchmarkMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "industry_id", nullable = false)
    private Industry industry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_benchmark_id", nullable = false)
    private CostBenchmark costBenchmark;

    public static IndustryCostBenchmarkMapping create(Industry industry, CostBenchmark costBenchmark) {
        IndustryCostBenchmarkMapping mapping =
                new IndustryCostBenchmarkMapping();

        mapping.industry = industry;
        mapping.costBenchmark = costBenchmark;

        return mapping;
    }
}

