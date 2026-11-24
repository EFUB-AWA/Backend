package com.irion.irion_backend.domain.propertyAnalaysis.repository;

import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface PropertyAnalysisQueryRepository extends Repository<PropertyAnalysis, Long> {

    @EntityGraph(attributePaths = "analysisRun")
    Optional<PropertyAnalysis> findByIdAndAnalysisRun_StartupCondition_User_Id(Long propertyAnalysisId, Long requesterId);
}
