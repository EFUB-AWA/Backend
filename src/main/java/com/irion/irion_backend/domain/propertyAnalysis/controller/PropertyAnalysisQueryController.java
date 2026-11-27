package com.irion.irion_backend.domain.propertyAnalysis.controller;

import com.irion.irion_backend.domain.propertyAnalysis.dto.PropertyAnalysisDetailResponse;
import com.irion.irion_backend.domain.propertyAnalysis.dto.StressTestListResponse;
import com.irion.irion_backend.domain.propertyAnalysis.dto.StressTestDetailResponse;
import com.irion.irion_backend.domain.propertyAnalysis.service.StressTestQueryService;
import com.irion.irion_backend.global.error.BusinessException;
import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.STRESS_TEST_FETCH_FAILED;
import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.STRESS_TEST_DETAIL_FETCH_FAILED;
import lombok.extern.slf4j.Slf4j;
import com.irion.irion_backend.domain.propertyAnalysis.service.PropertyAnalysisQueryService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/api/property-analyses")
@RequiredArgsConstructor
public class PropertyAnalysisQueryController {

    private final PropertyAnalysisQueryService propertyAnalysisQueryService;
    private final StressTestQueryService stressTestQueryService;

    @GetMapping("/{propertyAnalysisId}/stress-tests")
    public StressTestListResponse getStressTests(@AuthMember Long requesterId, @PathVariable Long propertyAnalysisId) {
        try {
            return stressTestQueryService.getScenarios(requesterId, propertyAnalysisId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch stress tests for property analysis {}", propertyAnalysisId, e);
            throw new BusinessException(STRESS_TEST_FETCH_FAILED);
        }
    }

    @GetMapping("/{propertyAnalysisId}")
    public PropertyAnalysisDetailResponse getDetail(@AuthMember Long requesterId, @PathVariable("propertyAnalysisId") Long propertyAnalysisId) {
        return propertyAnalysisQueryService.getDetail(requesterId, propertyAnalysisId);
    }

    @GetMapping("/{propertyAnalysisId}/stress-tests/{stressTestId}")
    public StressTestDetailResponse getStressTestDetail(@AuthMember Long requesterId,
                                                        @PathVariable Long propertyAnalysisId, @PathVariable Long stressTestId) {
        try {
            return stressTestQueryService.getScenario(requesterId, propertyAnalysisId, stressTestId);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to fetch stress test {} for property analysis {}", stressTestId, propertyAnalysisId, e);
            throw new BusinessException(STRESS_TEST_DETAIL_FETCH_FAILED);
        }
    }
}
