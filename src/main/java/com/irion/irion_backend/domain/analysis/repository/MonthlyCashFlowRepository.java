package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.MonthlyCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyCashFlowRepository extends JpaRepository<MonthlyCashFlow, Long> {}
