package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.calculation.FinancialCalculator;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.*;

class AnalysisWorkerTest {
    private final AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
    private final AnalysisRunRepository runs = mock(AnalysisRunRepository.class);
    private final FinancialCalculator calculator = mock(FinancialCalculator.class);
    private final AnalysisResultService results = mock(AnalysisResultService.class);
    private final AnalysisFailureService failures = mock(AnalysisFailureService.class);
    private final AnalysisCommercialStatistics statistics = mock(AnalysisCommercialStatistics.class);
    private final AnalysisWorker worker = new AnalysisWorker(executor, runs, calculator, results, failures, statistics);

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"COMPLETED", "FAILED"})
    void skipsTerminalRun(AnalysisStatus status) {
        var run = mock(AnalysisRun.class);
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(run.getStatus()).thenReturn(status);
        worker.process(1L);
        verifyNoInteractions(calculator, results, failures);
    }

    @Test
    void usesPreviouslyFrozenStatisticsWithoutFetchingAgain() {
        var run = mock(AnalysisRun.class);
        var snapshot = Map.<String, Object>of(AnalysisCommercialStatistics.SNAPSHOT_KEY, Map.of());
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(run.getStatus()).thenReturn(AnalysisStatus.PROCESSING);
        when(run.getConditionSnapshot()).thenReturn(snapshot);
        var failure = new IllegalStateException("policy unavailable");
        when(calculator.calculate(eq(snapshot), anyList())).thenThrow(failure);
        worker.process(1L);
        verify(failures).fail(1L, failure);
        verifyNoInteractions(statistics, results);
    }

    @Test
    void recordsRejectedSubmission() {
        var rejected = new RejectedExecutionException();
        doThrow(rejected).when(executor).execute(any(Runnable.class));
        worker.afterCommit(new AnalysisService.AnalysisStarted(1L));
        verify(failures).fail(1L, rejected);
        verifyNoInteractions(runs, calculator, results);
    }
}
