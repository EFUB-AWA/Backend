package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.aiConsultation.comparison.service.ComparisonConsultationService;
import com.irion.irion_backend.domain.aiConsultation.property.client.ConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.property.service.PropertyConsultationTransactions;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.service.AnalysisFailureService;
import com.irion.irion_backend.domain.analysis.service.AnalysisResultService;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;
import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=update", "seoul-open-api.refresh-enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepositoryIntegrationTest$HistorySqlInspector"})
@Sql(statements = "TRUNCATE users, industries, external_data_snapshots CASCADE")
@Sql({"/sql/property-consultation-test-data.sql", "/sql/analysis-delete-test-data.sql"})
class AnalysisDeletionIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withCopyFileToContainer(MountableFile.forClasspathResource("sql/comparison-legacy-schema.sql"),
                    "/docker-entrypoint-initdb.d/01-legacy.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("db/manual/04-decouple-comparison-consultations.sql"),
                    "/docker-entrypoint-initdb.d/04-comparison.sql");

    @Autowired AnalysisService analyses;
    @Autowired AnalysisResultService results;
    @Autowired AnalysisFailureService failures;
    @Autowired PropertyConsultationTransactions ai;
    @Autowired ComparisonConsultationService comparisons;
    @Autowired JdbcTemplate jdbc;

    private void delete() {
        analyses.delete(9001L, 9001L);
    }

    private void error(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getErrorCode());
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void deletesOnlyOwnedResultsAndPreservesIndependentComparisons() {
        var saved = comparisons.getComparisonConsultation(9001L, 1L);
        var temporary = comparisons.getComparisonConsultation(9001L, 2L);
        var list = comparisons.getComparisonConsultations(9001L, null, 10);
        var snapshots = jdbc.queryForList("select property_snapshots::text from comparison_ai_consultations order by id", String.class);
        delete();
        error(ANALYSIS_NOT_FOUND, this::delete);
        assertEquals(0, count("select count(*) from property_ai_consultations where id in (9001,9002,9003)"));
        assertEquals(0, count("select count(*) from property_analyses where analysis_run_id=9001"));
        assertEquals(3, count("select count(*) from property_analyses where analysis_run_id=9010"));
        assertEquals(1, count("select count(*) from property_ai_consultations where id=9101"));
        assertEquals(3, count("select count(*) from candidate_properties where startup_condition_id=9001"));
        assertEquals(1, count("select count(*) from startup_conditions where id=9001"));
        assertEquals(1, count("select count(*) from external_data_snapshots where id=7"));
        analyses.delete(9001L, 9010L);
        assertEquals(saved, comparisons.getComparisonConsultation(9001L, 1L));
        assertEquals(temporary, comparisons.getComparisonConsultation(9001L, 2L));
        assertEquals(list, comparisons.getComparisonConsultations(9001L, null, 10));
        assertEquals(snapshots, jdbc.queryForList("select property_snapshots::text from comparison_ai_consultations order by id", String.class));
        var firstSave = comparisons.saveComparisonConsultation(9001L, 2L);
        assertEquals(firstSave, comparisons.saveComparisonConsultation(9001L, 2L));
    }

    @Test
    void propertyDeleteFailureRollsBackConsultationDeletes() {
        jdbc.execute("create table deletion_test_reference (id bigint references property_analyses)");
        try {
            jdbc.update("insert into deletion_test_reference values (9001)");
            assertThrows(DataIntegrityViolationException.class, this::delete);
            assertEquals(3, count("select count(*) from property_ai_consultations where id in (9001,9002,9003)"));
            assertEquals(3, count("select count(*) from property_analyses where analysis_run_id=9001"));
            assertEquals(1, count("select count(*) from analysis_runs where id=9001"));
        } finally {
            jdbc.execute("drop table deletion_test_reference");
        }
    }

    @Test
    void concurrentDeletesReturnOneSuccessAndOneNotFound() throws Exception {
        ordered(this::delete, () -> error(ANALYSIS_NOT_FOUND, this::delete));
        assertEquals(0, count("select count(*) from analysis_runs where id=9001"));
    }

    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void completionOrFailureSerializesWithDeletion(boolean complete, boolean workerFirst) throws Exception {
        jdbc.update("delete from property_ai_consultations where id in (9001,9002,9003)");
        jdbc.update("delete from property_analyses where analysis_run_id=9001");
        jdbc.update("update analysis_runs set status='PROCESSING' where id=9001");
        Runnable finish = () -> {
            if (complete) {
                var rows = List.of(9001L, 9002L, 9003L).stream().map(id -> new CalculatedPropertyResult(id,
                        100L, 10L, BigDecimal.ONE, 50L, 20L, "ACHIEVED", 30L, 10L, BigDecimal.TEN, "A", 1, "reason")).toList();
                results.complete(9001L, rows, Map.of(), "v1", LocalDateTime.now(), 1);
            } else {
                failures.fail(9001L, new IllegalStateException("fixture"));
            }
        };
        if (workerFirst) {
            ordered(finish, this::delete);
        } else {
            ordered(() -> error(ANALYSIS_IN_PROGRESS, this::delete), finish);
            assertEquals(complete ? 3 : 0, count("select count(*) from property_analyses where analysis_run_id=9001"));
            delete();
        }
        assertEquals(0, count("select count(*) from property_analyses where analysis_run_id=9001"));
        error(ANALYSIS_NOT_FOUND, this::delete);
    }

    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void reservationOrRetrySerializesWithDeletion(boolean retry, boolean reserveFirst) throws Exception {
        if (!retry) jdbc.update("delete from property_ai_consultations where id=9002");
        Runnable reserve = retry ? () -> ai.reserveRetry(9001L, 9002L, 9002L) : () -> ai.reserve(9001L, 9002L);
        if (reserveFirst) {
            ordered(reserve, () -> error(PROPERTY_CONSULTATION_IN_PROGRESS, this::delete));
            assertEquals(1, count("select count(*) from property_ai_consultations where property_analysis_id=9002 and status='PENDING'"));
        } else {
            ordered(this::delete, () -> error(retry ? CONSULTATION_NOT_FOUND : PROPERTY_ANALYSIS_NOT_FOUND, reserve));
            error(ANALYSIS_NOT_FOUND, this::delete);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void aiCompletionOrFailureSerializesWithDeletion(boolean complete, boolean aiFirst) throws Exception {
        jdbc.update("update property_ai_consultations set status='PENDING' where id=9002");
        Runnable finish = complete ? () -> ai.complete(9002L,
                new ConsultationAiClient.Result("done", List.of(), List.of(), "note", "model", "v1")) : () -> ai.fail(9002L);
        if (aiFirst) {
            ordered(finish, this::delete);
        } else {
            ordered(() -> error(PROPERTY_CONSULTATION_IN_PROGRESS, this::delete), finish);
            assertEquals(complete ? "COMPLETED" : "FAILED",
                    jdbc.queryForObject("select status from property_ai_consultations where id=9002", String.class));
            delete();
        }
        error(ANALYSIS_NOT_FOUND, this::delete);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void detailAndHistoryKeepTheirSnapshotAcrossDelete(boolean detail) throws Exception {
        var oldDetail = analyses.getDetail(9001L, 9001L);
        var oldHistory = analyses.getHistory(9001L);
        var readingResults = new CountDownLatch(1);
        var deleted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var read = executor.submit(() -> observe(sql -> {
                if (sql.contains("from property_analyses")) {
                    readingResults.countDown();
                    await(deleted);
                }
            }, () -> {
                if (detail) assertEquals(oldDetail, analyses.getDetail(9001L, 9001L));
                else assertEquals(oldHistory, analyses.getHistory(9001L));
            }));
            await(readingResults);
            delete();
            deleted.countDown();
            read.get(15, TimeUnit.SECONDS);
            error(ANALYSIS_NOT_FOUND, () -> analyses.getDetail(9001L, 9001L));
            assertTrue(analyses.getHistory(9001L).analyses().stream().noneMatch(r -> r.analysisRunId() == 9001L));
        } finally {
            deleted.countDown();
            executor.shutdownNow();
        }
    }

    // Hold the first transaction's locks through the start of the other transaction's first query.
    private void ordered(Runnable first, Runnable second) throws Exception {
        var finishing = new CountDownLatch(1);
        var competing = new CountDownLatch(1);
        var registered = new AtomicBoolean();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var leader = executor.submit(() -> observe(sql -> {
                if (registered.compareAndSet(false, true)) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCompletion() {
                            finishing.countDown();
                            await(competing);
                        }
                    });
                }
            }, first));
            await(finishing);
            var follower = executor.submit(() -> observe(sql -> competing.countDown(), second));
            leader.get(15, TimeUnit.SECONDS);
            follower.get(15, TimeUnit.SECONDS);
        } finally {
            competing.countDown();
            executor.shutdownNow();
        }
    }

    private static void observe(Consumer<String> observer, Runnable action) {
        AnalysisRunRepositoryIntegrationTest.HistorySqlInspector.observe(observer, () -> {
            action.run();
            return null;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for concurrent transaction");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
