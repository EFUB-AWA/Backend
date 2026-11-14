package com.irion.irion_backend.domain.industry.repository;

import com.irion.irion_backend.domain.industry.entity.Industry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryRepository
        extends JpaRepository<Industry, Long> {
}
