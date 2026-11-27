package com.irion.irion_backend.domain.propertyAnalysis.service;

import com.irion.irion_backend.domain.analysis.repository.StressTestRepository;
import com.irion.irion_backend.domain.analysis.repository.MonthlyCashFlowRepository;
import com.irion.irion_backend.domain.propertyAnalysis.dto.CashFlowResponse;
import com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode;
import com.irion.irion_backend.domain.propertyAnalysis.dto.StressTestListResponse;
import com.irion.irion_backend.domain.propertyAnalysis.dto.StressTestDetailResponse;
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
    private final MonthlyCashFlowRepository monthlyCashFlows;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StressTestListResponse getScenarios(Long requesterId, Long propertyAnalysisId) {
        requireOwner(requesterId, propertyAnalysisId, PROPERTY_ANALYSIS_ACCESS_DENIED);
        var rows = stressTests.findByPropertyAnalysis_IdOrderBySalesDecreasePercentAsc(propertyAnalysisId);
        if (rows.isEmpty()) throw new BusinessException(STRESS_TEST_NOT_READY);
        return new StressTestListResponse(propertyAnalysisId,
                rows.stream().map(StressTestListResponse.Scenario::from).toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StressTestDetailResponse getScenario(Long requesterId, Long propertyAnalysisId, Long stressTestId) {
        requireOwner(requesterId, propertyAnalysisId, PROPERTY_ANALYSIS_ACCESS_DENIED);
        var row = stressTests.findById(stressTestId)
                .orElseThrow(() -> new BusinessException(STRESS_TEST_NOT_FOUND));
        if (!propertyAnalysisId.equals(row.getPropertyAnalysis().getId())) {
            throw new BusinessException(STRESS_TEST_PROPERTY_MISMATCH);
        }
        return StressTestDetailResponse.from(row);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CashFlowResponse getCashFlows(Long requesterId, Long stressTestId) {
        var scenario = stressTests.findById(stressTestId)
                .orElseThrow(() -> new BusinessException(STRESS_TEST_NOT_FOUND));
        requireOwner(requesterId, scenario.getPropertyAnalysis().getId(), STRESS_TEST_ACCESS_DENIED);
        var months = monthlyCashFlows.findByStressTest_IdOrderByMonthIndexAsc(stressTestId);
        if (months.isEmpty()) throw new BusinessException(CASH_FLOW_NOT_FOUND);
        return new CashFlowResponse(scenario.getId(), scenario.getSalesDecreasePercent(),
                months.stream().map(CashFlowResponse.Month::from).toList());
    }

    private void requireOwner(Long requesterId, Long propertyAnalysisId, PropertyAnalysisErrorCode denied) {
        var ownerId = propertyAnalyses.findOwnerIdByPropertyAnalysisId(propertyAnalysisId)
                .orElseThrow(() -> new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND));
        if (!ownerId.equals(requesterId)) throw new BusinessException(denied);
    }
}
