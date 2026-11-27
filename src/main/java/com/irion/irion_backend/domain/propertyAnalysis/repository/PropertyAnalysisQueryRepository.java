package com.irion.irion_backend.domain.propertyAnalysis.repository;

import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface PropertyAnalysisQueryRepository extends Repository<PropertyAnalysis, Long> {

    @Query("select p.analysisRun.startupCondition.user.id from PropertyAnalysis p where p.id = :propertyAnalysisId")
    Optional<Long> findOwnerIdByPropertyAnalysisId(Long propertyAnalysisId);

    @EntityGraph(attributePaths = "analysisRun")
    Optional<PropertyAnalysis> findByIdAndAnalysisRun_StartupCondition_User_Id(Long propertyAnalysisId, Long requesterId);
}
