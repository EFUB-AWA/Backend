package com.irion.irion_backend.domain.aiConsultation.property.repository;

import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyConsultationRepository extends JpaRepository<PropertyConsultation, Long> {
}
