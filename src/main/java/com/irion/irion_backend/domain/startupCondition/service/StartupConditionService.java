package com.irion.irion_backend.domain.startupCondition.service;

import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import com.irion.irion_backend.domain.startupCondition.dto.request.StartupConditionCreateRequest;
import com.irion.irion_backend.domain.startupCondition.dto.response.StartupConditionCreateResponse;
import com.irion.irion_backend.domain.startupCondition.dto.response.StartupConditionDetailResponse;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.exception.StartupConditionErrorCode;
import com.irion.irion_backend.domain.startupCondition.repository.StartupConditionRepository;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.domain.user.exception.UserErrorCode;
import com.irion.irion_backend.domain.user.repository.UserRepository;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.error.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StartupConditionService {

    private final StartupConditionRepository startupConditionRepository;
    private final UserRepository userRepository;
    private final IndustryRepository industryRepository;

    // 요청자의 id와 일치하는 창업 조건 찾기
    public StartupCondition findOwnedCondition(Long conditionId, Long requesterId) {
        return startupConditionRepository.findByIdAndUser_Id(conditionId, requesterId)
                .orElseThrow(() -> new BusinessException(StartupConditionErrorCode.STARTUP_CONDITION_NOT_FOUND));
    }

    @Transactional
    public StartupConditionCreateResponse createCondition(Long requesterId, StartupConditionCreateRequest request) {

        //창업 조건 중복 등록 확인
        if (startupConditionRepository.existsByUser_Id(requesterId)) {
            throw new BusinessException(StartupConditionErrorCode.STARTUP_CONDITION_ALREADY_EXISTS);
        }

        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Industry industry = industryRepository.findById(request.industryId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INVALID_INPUT));

        StartupCondition condition = StartupCondition.builder()
                .user(user)
                .industry(industry)
                .title(request.title())
                .preferredRegionCode(request.preferredRegionCode())
                .totalCapital(request.totalCapital())
                .availableCapital(request.availableCapital())
                .loanAmount(request.loanAmount())
                .monthlyLaborCost(request.monthlyLaborCost())
                .costOfSalesRate(request.costOfSalesRate())
                .monthlyOtherOperatingCost(request.monthlyOtherOperatingCost())
                .build();

        startupConditionRepository.save(condition);

        return StartupConditionCreateResponse.from(condition);
    }

    //창업 조건 상세 조회
    public StartupConditionDetailResponse getMyCondition(Long requesterId) {

        StartupCondition condition = startupConditionRepository.findByUser_Id(requesterId)
                .orElseThrow(() -> new BusinessException(StartupConditionErrorCode.STARTUP_CONDITION_NOT_FOUND));

        return StartupConditionDetailResponse.from(condition);
    }

}
