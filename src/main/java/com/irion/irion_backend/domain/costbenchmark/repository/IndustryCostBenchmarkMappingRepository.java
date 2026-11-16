package com.irion.irion_backend.domain.costbenchmark.repository;

import com.irion.irion_backend.domain.costbenchmark.entity.IndustryCostBenchmarkMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndustryCostBenchmarkMappingRepository extends JpaRepository<IndustryCostBenchmarkMapping, Long> {

    Optional<IndustryCostBenchmarkMapping> findByIndustryId(Long industryId);
    boolean existsByIndustryId(Long industryId);
}
