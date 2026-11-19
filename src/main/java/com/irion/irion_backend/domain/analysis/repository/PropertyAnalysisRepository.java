package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyAnalysisRepository extends JpaRepository<PropertyAnalysis, Long> {

    List<PropertyAnalysis> findByAnalysisRun_Id(Long analysisRunId);
}
