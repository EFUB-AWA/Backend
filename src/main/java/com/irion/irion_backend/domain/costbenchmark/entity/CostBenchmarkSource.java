package com.irion.irion_backend.domain.costbenchmark.entity;

import com.irion.irion_backend.domain.externaldata.entity.ExternalDataSnapshot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cost_benchmark_sources",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cost_benchmark_source",
                        columnNames = {"cost_benchmark_id", "external_data_snapshot_id"}
                )
        }
)
public class CostBenchmarkSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_benchmark_id", nullable = false)
    private CostBenchmark costBenchmark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_data_snapshot_id", nullable = false)
    private ExternalDataSnapshot externalDataSnapshot;


    public static CostBenchmarkSource create(
            CostBenchmark costBenchmark,
            ExternalDataSnapshot externalDataSnapshot
    ) {
        CostBenchmarkSource source = new CostBenchmarkSource();

        source.costBenchmark = costBenchmark;
        source.externalDataSnapshot = externalDataSnapshot;

        return source;
    }

}

