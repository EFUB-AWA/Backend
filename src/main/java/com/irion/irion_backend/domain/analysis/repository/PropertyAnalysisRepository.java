package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyAnalysisRepository extends JpaRepository<PropertyAnalysis, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropertyAnalysis p where p.id = :id")
    Optional<PropertyAnalysis> findForConsultation(@Param("id") Long id);

    List<PropertyAnalysis> findByAnalysisRun_Id(Long analysisRunId);
}
