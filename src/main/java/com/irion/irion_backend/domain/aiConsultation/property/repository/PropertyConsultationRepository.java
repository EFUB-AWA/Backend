package com.irion.irion_backend.domain.aiConsultation.property.repository;

import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;

public interface PropertyConsultationRepository extends JpaRepository<PropertyConsultation, Long> {

    String FOR_RUN = " where c.propertyAnalysis.id in (select p.id from PropertyAnalysis p where p.analysisRun.id = :runId)";

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PropertyConsultation c" + FOR_RUN + " order by c.id")
    List<PropertyConsultation> lockForDeletion(@Param("runId") Long runId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PropertyConsultation c" + FOR_RUN)
    int deleteForRun(@Param("runId") Long runId);

    Optional<PropertyConsultation> findByPropertyAnalysis_Id(Long propertyAnalysisId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropertyConsultation p where p.id = :id")
    Optional<PropertyConsultation> findForUpdate(@Param("id") Long id);

    @Query("""
        SELECT p FROM PropertyConsultation p JOIN p.propertyAnalysis pa
        JOIN pa.candidateProperty c JOIN c.startupCondition s
        WHERE s.user.id = :requesterId and p.id = :propertyConsultationId
    """)
    Optional<PropertyConsultation> findOwnedById(@Param("propertyConsultationId") Long propertyConsultationId,
                                                 @Param("requesterId") Long requesterId);
}
