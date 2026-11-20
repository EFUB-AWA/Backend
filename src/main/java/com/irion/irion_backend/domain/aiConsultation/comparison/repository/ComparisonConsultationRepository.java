package com.irion.irion_backend.domain.aiConsultation.comparison.repository;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ComparisonConsultationRepository extends JpaRepository<ComparisonConsultation, Long> {

    @Query("""
            SELECT c FROM ComparisonConsultation c
            JOIN c.analysisRun a JOIN a.startupCondition s
            WHERE s.user.id = :requesterId and c.id = :comparisonConsultationId
        """)
    Optional<ComparisonConsultation> findOwnedById(@Param("comparisonConsultationId") Long comparisonConsultationId,
                                                   @Param("requesterId") Long requesterId);
}
