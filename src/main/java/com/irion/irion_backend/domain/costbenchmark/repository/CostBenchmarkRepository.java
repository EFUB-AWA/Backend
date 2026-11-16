package com.irion.irion_backend.domain.costbenchmark.repository;

import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CostBenchmarkRepository extends JpaRepository<CostBenchmark, Long> {

    Optional<CostBenchmark> findByKosisIndustryCodeAndRegionCodeAndReferenceYear(
            String kosisIndustryCode,
            String regionCode,
            Integer referenceYear
    );

}
