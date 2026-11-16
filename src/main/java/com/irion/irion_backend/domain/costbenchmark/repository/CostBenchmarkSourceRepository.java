package com.irion.irion_backend.domain.costbenchmark.repository;

import com.irion.irion_backend.domain.costbenchmark.entity.CostBenchmarkSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CostBenchmarkSourceRepository extends JpaRepository<CostBenchmarkSource, Long> {

    List<CostBenchmarkSource> findByCostBenchmarkId(Long costBenchmarkId);
    void deleteByCostBenchmarkId(Long costBenchmarkId);
}

