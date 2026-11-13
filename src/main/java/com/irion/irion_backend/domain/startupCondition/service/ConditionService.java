package com.irion.irion_backend.domain.startupCondition.service;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.exception.ConditionErrorCode;
import com.irion.irion_backend.domain.startupCondition.repository.StartupConditionRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConditionService {

    private final StartupConditionRepository conditionRepository;

    // 요청자의 id와 일치하는 창업 조건 찾기
    public StartupCondition findOwnedCondition(Long conditionId, Long requesterId) {
        return conditionRepository.findByIdAndUser_Id(conditionId, requesterId)
                .orElseThrow(() -> new BusinessException(ConditionErrorCode.STARTUP_CONDITION_NOT_FOUND));
    }
}
