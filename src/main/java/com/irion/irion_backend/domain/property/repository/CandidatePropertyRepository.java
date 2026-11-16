package com.irion.irion_backend.domain.property.repository;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidatePropertyRepository extends JpaRepository<CandidateProperty, Long> {

    // 창업 조건 별 목록 조회
    List<CandidateProperty> findByStartupConditionOrderByCreatedAtDesc(Long startupConditionId);

    // 상세 조회
    Optional<CandidateProperty> findByIdAndStartupCondition_User_Id(Long propertyId, Long userId);
}
