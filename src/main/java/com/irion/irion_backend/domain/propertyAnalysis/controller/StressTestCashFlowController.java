package com.irion.irion_backend.domain.propertyAnalysis.controller;

import com.irion.irion_backend.domain.propertyAnalysis.dto.CashFlowResponse;
import com.irion.irion_backend.domain.propertyAnalysis.service.StressTestQueryService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.CASH_FLOW_FETCH_FAILED;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StressTestCashFlowController {
    private final StressTestQueryService stressTests;

    @GetMapping("/api/stress-tests/{stressTestId}/cash-flows")
    public CashFlowResponse getCashFlows(@AuthMember Long requesterId, @PathVariable Long stressTestId) {
        try {
            return stressTests.getCashFlows(requesterId, stressTestId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch cash flows for stress test {}", stressTestId, e);
            throw new BusinessException(CASH_FLOW_FETCH_FAILED);
        }
    }
}
