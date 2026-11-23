package com.irion.irion_backend.domain.aiConsultation.property.repository;

import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PropertyConsultationRepository extends JpaRepository<PropertyConsultation, Long> {

    Optional<PropertyConsultation> findByPropertyAnalysis_Id(Long propertyAnalysisId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropertyConsultation p where p.id = :id")
    Optional<PropertyConsultation> findForRetry(@Param("id") Long id);

    @Query("""
        SELECT p FROM PropertyConsultation p JOIN p.propertyAnalysis pa
        JOIN pa.candidateProperty c JOIN c.startupCondition s
        WHERE s.user.id = :requesterId and p.id = :propertyConsultationId
    """)
    Optional<PropertyConsultation> findOwnedById(@Param("propertyConsultationId") Long propertyConsultationId,
                                                 @Param("requesterId") Long requesterId);
}
