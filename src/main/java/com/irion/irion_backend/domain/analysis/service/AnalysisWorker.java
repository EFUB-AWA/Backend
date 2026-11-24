package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.calculation.FinancialCalculator;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
@Profile("analysis-api")
public class AnalysisWorker {
    private final AsyncTaskExecutor executor;
    private final AnalysisRunRepository runs;
    private final FinancialCalculator calculator;
    private final AnalysisCommercialStatistics statistics;
    private final AnalysisResultService results;
    private final AnalysisFailureService failures;

    public AnalysisWorker(@Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor, AnalysisRunRepository runs,
                          FinancialCalculator calculator, AnalysisResultService results, AnalysisFailureService failures,
                          AnalysisCommercialStatistics statistics) {
        this.executor = executor;
        this.runs = runs;
        this.statistics = statistics;
        this.calculator = calculator;
        this.results = results;
        this.failures = failures;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(AnalysisService.AnalysisStarted event) {
        try {
            executor.execute(() -> process(event.runId()));
        } catch (RejectedExecutionException rejected) {
            failures.fail(event.runId(), rejected);
        }
    }

    void process(Long runId) {
        FinancialCalculator.Result result;
        try {
            var run = runs.findById(runId).orElseThrow();
            if (run.getStatus() != AnalysisStatus.PROCESSING) {
                return;
            }
            var properties = run.getPropertySnapshots();
            var condition = Map.copyOf(run.getConditionSnapshot());
            if (!condition.containsKey(AnalysisCommercialStatistics.SNAPSHOT_KEY)) {
                var observation = statistics.collect(condition, properties);
                condition = results.freezeStatistics(runId, observation);
            }
            result = Objects.requireNonNull(calculator.calculate(condition, properties), "Calculation returned no result");
        } catch (RuntimeException cause) {
            failures.fail(runId, cause);
            return;
        }
        try {
            results.complete(runId, result.properties(), result.assumptions(), result.calculationVersion(),
                    result.dataCutoffAt(), result.horizonMonths());
        } catch (RuntimeException cause) {
            // The result service owns rollback and the separate FAILED transaction.
            log.error("Cannot persist results for analysis run {}", runId, cause);
        }
    }
}
