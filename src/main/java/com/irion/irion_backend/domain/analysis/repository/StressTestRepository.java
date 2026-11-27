package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.StressTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StressTestRepository extends JpaRepository<StressTest, Long> {}
