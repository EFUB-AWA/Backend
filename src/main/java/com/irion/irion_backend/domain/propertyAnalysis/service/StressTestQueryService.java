package com.irion.irion_backend.domain.propertyAnalysis.service;

import com.irion.irion_backend.domain.analysis.repository.StressTestRepository;
import com.irion.irion_backend.domain.propertyAnalysis.dto.StressTestListResponse;
import com.irion.irion_backend.domain.propertyAnalysis.repository.PropertyAnalysisQueryRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.*;

@Service
@RequiredArgsConstructor
public class StressTestQueryService {
    private final PropertyAnalysisQueryRepository propertyAnalyses;
    private final StressTestRepository stressTests;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StressTestListResponse getScenarios(Long requesterId, Long propertyAnalysisId) {
        var ownerId = propertyAnalyses.findOwnerIdByPropertyAnalysisId(propertyAnalysisId)
                .orElseThrow(() -> new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND));
        if (!ownerId.equals(requesterId)) throw new BusinessException(PROPERTY_ANALYSIS_ACCESS_DENIED);
        var rows = stressTests.findByPropertyAnalysis_IdOrderBySalesDecreasePercentAsc(propertyAnalysisId);
        if (rows.isEmpty()) throw new BusinessException(STRESS_TEST_NOT_READY);
        return new StressTestListResponse(propertyAnalysisId,
                rows.stream().map(StressTestListResponse.Scenario::from).toList());
    }
}
