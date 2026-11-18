package com.irion.irion_backend.domain.aiConsultation.property.repository;

import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyConsultationRepository extends JpaRepository<PropertyConsultation, Long> {

    Optional<PropertyConsultation> findByPropertyAnalysis_Id(Long analysisId);
}
