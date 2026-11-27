package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.StressTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StressTestRepository extends JpaRepository<StressTest, Long> {
    List<StressTest> findByPropertyAnalysis_IdOrderBySalesDecreasePercentAsc(Long propertyAnalysisId);
}
