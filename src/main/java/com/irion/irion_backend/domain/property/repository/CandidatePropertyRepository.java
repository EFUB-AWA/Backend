package com.irion.irion_backend.domain.property.repository;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatePropertyRepository extends JpaRepository<CandidateProperty, Long> {
}
