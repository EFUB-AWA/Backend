package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    private final AnalysisRunRepository runs = mock(AnalysisRunRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AnalysisService statusService = new AnalysisService(mock(AnalysisRequestValidator.class),
            new AnalysisSnapshotFactory(), runs, events);

    private AnalysisRun storedRun() {
        var owner = mock(User.class);
        var condition = mock(StartupCondition.class);
        var run = mock(AnalysisRun.class);
        when(owner.getId()).thenReturn(7L);
        when(condition.getUser()).thenReturn(owner);
        when(run.getStartupCondition()).thenReturn(condition);
        when(run.getId()).thenReturn(100L);
        when(runs.findById(100L)).thenReturn(Optional.of(run));
        return run;
    }

    @ParameterizedTest
    @EnumSource(AnalysisStatus.class)
    void readsOwnedStatusWithoutChangingRun(AnalysisStatus status) {
        var run = storedRun();
        var storedTime = LocalDateTime.of(2026, 11, 10, 12, 0);
        when(run.getStatus()).thenReturn(status);
        when(run.getCompletedAt()).thenReturn(storedTime);

        var response = statusService.getStatus(7L, 100L);

        assertEquals(100L, response.analysisRunId());
        assertEquals(status, response.status());
        assertEquals(status == AnalysisStatus.COMPLETED ? storedTime : null, response.completedAt());
        verify(runs).findById(100L);
        verifyNoMoreInteractions(runs);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 8L)
    void rejectsNonOwner(Long requesterId) {
        storedRun();
        var error = assertThrows(BusinessException.class, () -> statusService.getStatus(requesterId, 100L));
        assertSame(AnalysisErrorCode.ANALYSIS_ACCESS_DENIED, error.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, 100L})
    void reportsMissingRun(long id) {
        var error = assertThrows(BusinessException.class, () -> statusService.getStatus(7L, id));
        assertSame(AnalysisErrorCode.ANALYSIS_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void propagatesRepositoryFailure() {
        var failure = new IllegalStateException("lookup failed");
        when(runs.findById(100L)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> statusService.getStatus(7L, 100L)));
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisErrorCode.class, names = {"ANALYSIS_ACCESS_DENIED", "PROPERTY_MAPPING_NOT_COMPLETED"})
    void validationFailureStopsBeforeSaving(AnalysisErrorCode code) {
        var validator = mock(AnalysisRequestValidator.class);
        var repository = mock(AnalysisRunRepository.class);
        var service = new AnalysisService(validator, new AnalysisSnapshotFactory(), repository,
                mock(org.springframework.context.ApplicationEventPublisher.class));
        var request = new AnalysisRunRequest(1L, List.of(2L), "analysis");
        when(validator.validate(3L, request)).thenThrow(new BusinessException(code));
        assertSame(code, assertThrows(BusinessException.class, () -> service.start(3L, request)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
