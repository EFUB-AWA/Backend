package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
}
