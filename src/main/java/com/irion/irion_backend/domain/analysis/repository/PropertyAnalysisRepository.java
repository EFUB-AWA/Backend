package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface PropertyAnalysisRepository extends JpaRepository<PropertyAnalysis, Long> {
    @Query("select distinct p.analysisRun.id from PropertyAnalysis p where p.id in :ids order by p.analysisRun.id")
    List<Long> findSourceRunIds(Collection<Long> ids);

    @Query("""
        select p from PropertyAnalysis p join fetch p.analysisRun r join fetch r.startupCondition s
        join fetch p.candidateProperty cp join fetch cp.startupCondition where p.id in :ids
        """)
    List<PropertyAnalysis> findComparisonSources(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropertyAnalysis p where p.analysisRun.id = :runId order by p.id")
    List<PropertyAnalysis> lockForDeletion(@Param("runId") Long runId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PropertyAnalysis p where p.analysisRun.id = :runId")
    int deleteForRun(@Param("runId") Long runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropertyAnalysis p where p.id = :id")
    Optional<PropertyAnalysis> findForConsultation(@Param("id") Long id);

    List<PropertyAnalysis> findByAnalysisRun_Id(Long analysisRunId);

    List<PropertyAnalysis> findByAnalysisRun_IdIn(Collection<Long> runIds);
}
