package com.irion.irion_backend.domain.startupCondition.repository;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StartupConditionRepository extends JpaRepository<StartupCondition, Long> {

    Optional<StartupCondition> findByIdAndUser_Id(Long conditionId, Long userId);
}
