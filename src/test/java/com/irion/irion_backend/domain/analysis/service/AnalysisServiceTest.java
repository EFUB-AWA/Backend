package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    @ParameterizedTest
    @EnumSource(value = AnalysisErrorCode.class, names = {"ANALYSIS_ACCESS_DENIED", "PROPERTY_MAPPING_NOT_COMPLETED"})
    void validationFailureStopsBeforeSaving(AnalysisErrorCode code) {
        var validator = mock(AnalysisRequestValidator.class);
        var repository = mock(AnalysisRunRepository.class);
        var service = new AnalysisService(validator, new AnalysisSnapshotFactory(), repository);
        var request = new AnalysisRunRequest(1L, List.of(2L), "analysis");
        when(validator.validate(3L, request)).thenThrow(new BusinessException(code));
        assertSame(code, assertThrows(BusinessException.class, () -> service.start(3L, request)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
