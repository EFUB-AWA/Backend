package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.calculation.FinancialCalculator;
import com.irion.irion_backend.domain.analysis.service.AnalysisWorker;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.service.AnalysisFailureService;
import com.irion.irion_backend.domain.analysis.service.AnalysisResultService;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/irion_dev?currentSchema=public"
})
@Transactional
class AnalysisRunRepositoryIntegrationTest {

    @Autowired private AnalysisRunRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnalysisService service;

    @Test
    void defaultContextHasNoAnalysisWorker(@Autowired ObjectProvider<AnalysisWorker> workers) {
        assertNull(workers.getIfAvailable());
    }

    @Test
    void restoresOrderedSnapshotsFromPostgresAfterClearingPersistenceContext() {
        StartupCondition condition = persistCondition();
        var first = snapshot(42L, "48.20");
        var second = snapshot(7L, "19.05");
        var snapshots = List.of(first, second, first);
        var ids = List.of(42L, 7L, 42L);
        var conditionSnapshot = Map.<String, Object>of("schemaVersion", 1, "title", condition.getTitle());
        AnalysisRun saved = repository.saveAndFlush(
                AnalysisRun.create(condition, "round trip", ids, conditionSnapshot, snapshots));
        Long runId = saved.getId();
        entityManager.clear();

        assertEquals("array", jdbc.queryForObject(
                "select jsonb_typeof(property_snapshots) from analysis_runs where id = ?", String.class, runId));
        AnalysisRun restored = repository.findById(runId).orElseThrow();
        assertEquals(snapshots, restored.getPropertySnapshots());
        assertEquals(ids, restored.getCandidatePropertyIds());
        assertEquals(conditionSnapshot, restored.getConditionSnapshot());
        assertEquals(AnalysisStatus.PROCESSING, restored.getStatus());
    }

    @Test
    void startsProcessingRunWithValidatedInputsInRequestOrder() {
        StartupCondition condition = persistCondition();
        Long first = persistProperty(condition, "first");
        Long second = persistProperty(condition, "second");
        var ids = List.of(second, first, second);
        var response = service.start(condition.getUser().getId(), new AnalysisRunRequest(condition.getId(), ids, "run"));
        entityManager.clear();
        var run = repository.findById(response.analysisRunId()).orElseThrow();
        assertEquals(AnalysisStatus.PROCESSING, response.status());
        assertEquals(response.status(), run.getStatus());
        assertEquals("run", run.getTitle());
        assertNull(run.getAssumptionsSnapshot());
        assertEquals(ids, run.getCandidatePropertyIds());
        assertEquals(List.of("second", "first", "second"), run.getPropertySnapshots().stream()
                .map(PropertySnapshotV1::name).toList());
        assertEquals(new PropertySnapshotV1(1, second, "second", "address", null, 37.5, 127.0,
                new BigDecimal("48.20"), 100L, 20L, 3L, 5L, null, MappingStatus.MAPPED, "A1"),
                run.getPropertySnapshots().get(0));
        var snapshot = run.getConditionSnapshot();
        assertEquals(condition.getId().longValue(), ((Number) snapshot.get("id")).longValue());
        assertEquals(condition.getUser().getId().longValue(), ((Number) snapshot.get("userId")).longValue());
        assertEquals(condition.getIndustry().getId().longValue(), ((Number) snapshot.get("industryId")).longValue());
        assertEquals(1, snapshot.get("schemaVersion"));
        assertEquals("snapshot condition", snapshot.get("title"));
        assertEquals("11440", snapshot.get("preferredRegionCode"));
        assertEquals(100, snapshot.get("totalCapital"));
        assertEquals(70, snapshot.get("availableCapital"));
        assertEquals(30, snapshot.get("loanAmount"));
        assertEquals(10, snapshot.get("monthlyLaborCost"));
        assertEquals("20.00", snapshot.get("costOfSalesRate"));
        assertEquals(5, snapshot.get("monthlyOtherOperatingCost"));
    }

    @Test
    void storedSnapshotKeepsRequestTimeValuesAfterPropertyUpdate() {
        StartupCondition condition = persistCondition();
        Long id = persistProperty(condition, "original");
        var response = service.start(condition.getUser().getId(),
                new AnalysisRunRequest(condition.getId(), List.of(id), "snapshot history"));

        entityManager.find(CandidateProperty.class, id).updatePropertyDetails(
                "updated", new BigDecimal("60.30"), 200L, 40L, 6L, 10L);
        entityManager.flush();
        entityManager.clear();

        assertEquals(40L, entityManager.find(CandidateProperty.class, id).getMonthlyRent());
        var run = repository.findById(response.analysisRunId()).orElseThrow();
        assertEquals(List.of(new PropertySnapshotV1(1, id, "original", "address", null, 37.5, 127.0,
                new BigDecimal("48.20"), 100L, 20L, 3L, 5L, null, MappingStatus.MAPPED, "A1")),
                run.getPropertySnapshots());
    }

    @Test
    void failedSaveMarksSharedTransactionForRollback() {
        StartupCondition condition = persistCondition();
        Long property = persistProperty(condition, "rollback");
        var request = new AnalysisRunRequest(condition.getId(), List.of(property), "x".repeat(256));
        assertThrows(DataIntegrityViolationException.class, () -> service.start(condition.getUser().getId(), request));
        TestTransaction.flagForCommit();
        assertThrows(org.springframework.transaction.UnexpectedRollbackException.class, TestTransaction::end);
        assertEquals(0, jdbc.queryForObject("select count(*) from startup_conditions where id = ?",
                Integer.class, condition.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from analysis_runs where startup_condition_id = ?",
                Integer.class, condition.getId()));
    }

    @Nested
    class ResultTransactions {
        @Autowired private AnalysisResultService resultService;
        @Autowired private AnalysisFailureService failureService;
        @Autowired private PropertyAnalysisRepository results;
        private Long runId, first, second, conditionId, userId, industryId;
        private final LocalDateTime cutoff = LocalDateTime.of(2025, 1, 1, 0, 0);

        @BeforeEach
        void commitInputForIndependentTransactions() {
            var condition = persistCondition();
            conditionId = condition.getId();
            userId = condition.getUser().getId();
            industryId = condition.getIndustry().getId();
            first = persistProperty(condition, "first");
            second = persistProperty(condition, "second");
            runId = service.start(userId, new AnalysisRunRequest(conditionId, List.of(second, first), "results"))
                    .analysisRunId();
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }

        @AfterEach
        void removeCommittedFixture() {
            cleanUp(runId, conditionId, userId, industryId);
        }

        private CalculatedPropertyResult result(Long id, String grade) {
            return new CalculatedPropertyResult(id, 111L, 22L, new BigDecimal("0.30"), 33L, 44L,
                    "test status", 55L, 66L, new BigDecimal("0.80"), grade, 1, "test reason");
        }

        private void complete(List<CalculatedPropertyResult> rows) {
            resultService.complete(runId, rows, Map.of("source", "test"), "test-v1", cutoff, 3);
        }

        @Test
        void storesSnapshotAndMetadataOnceAndPreservesCompletedState() {
            jdbc.update("update candidate_properties set monthly_rent = 999 where id = ?", first);
            var rows = List.of(result(second, "second grade"), result(first, "first grade"));
            complete(rows);
            complete(rows);
            failureService.fail(runId, new IllegalStateException("late failure"));
            var completed = repository.findById(runId).orElseThrow();
            assertEquals(AnalysisStatus.COMPLETED, completed.getStatus());
            assertEquals(Map.of("source", "test"), completed.getAssumptionsSnapshot());
            assertEquals("test-v1", completed.getCalculationVersion());
            assertEquals(cutoff, completed.getDataCutoffAt());
            assertEquals(3, completed.getHorizonMonths());
            var stored = results.findByAnalysisRun_Id(runId);
            assertEquals(2, stored.size());
            for (var row : stored) {
                var snapshot = row.getPropertySnapshot();
                assertEquals(row.getCandidateProperty().getId().longValue(),
                        ((Number) snapshot.get("candidatePropertyId")).longValue());
                assertEquals(snapshot.get("name") + " grade", row.getSafetyGrade());
                assertEquals(20, snapshot.get("monthlyRent"));
                assertEquals("48.20", snapshot.get("areaM2"));
                assertEquals(111L, row.getExpectedMonthlySales());
            }
        }

        @Test
        void secondInsertFailureRollsBackFirstRowAndCommitsFailureSeparately() {
            assertThrows(DataIntegrityViolationException.class,
                    () -> complete(List.of(result(second, "valid"), result(first, "x".repeat(256)))));
            complete(List.of(result(second, "valid"), result(first, "valid")));
            failureService.fail(runId, new IllegalStateException("retry"));
            assertFailedWithoutRows();
        }

        @ParameterizedTest
        @ValueSource(strings = {"missing", "reordered", "null value"})
        void refusesIncompleteOrUnmatchedResults(String invalid) {
            var rows = switch (invalid) {
                case "missing" -> List.of(result(second, "test"));
                case "reordered" -> List.of(result(first, "test"), result(second, "test"));
                default -> List.of(result(second, "test"), result(first, null));
            };
            assertThrows(RuntimeException.class, () -> complete(rows));
            assertFailedWithoutRows();
        }

        private void assertFailedWithoutRows() {
            var run = repository.findById(runId).orElseThrow();
            assertEquals(AnalysisStatus.FAILED, run.getStatus());
            assertNull(run.getCompletedAt());
            assertNull(run.getCalculationVersion());
            assertEquals(0, results.findByAnalysisRun_Id(runId).size());
        }
    }

    @Nested
    @ActiveProfiles("analysis-api")
    class WorkerTransactions {
        @MockitoBean private FinancialCalculator calculator;

        @ParameterizedTest
        @ValueSource(strings = {"success", "calculation error", "storage error"})
        void processesCommittedSnapshotsOnWorkerThread(String scenario) {
            var condition = persistCondition();
            Long first = persistProperty(condition, "first");
            Long second = persistProperty(condition, "second");
            var caller = Thread.currentThread();
            when(calculator.calculate(anyMap(), anyList())).thenAnswer(invocation -> {
                assertNotEquals(caller, Thread.currentThread());
                List<PropertySnapshotV1> snapshots = invocation.getArgument(1);
                assertEquals(List.of(first, second), snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList());
                assertEquals(20L, snapshots.get(0).monthlyRent());
                Map<String, Object> conditionSnapshot = invocation.getArgument(0);
                assertEquals("snapshot condition", conditionSnapshot.get("title"));
                if (scenario.equals("calculation error")) {
                    throw new IllegalStateException("test calculation failure");
                }
                var rows = snapshots.stream().map(snapshot -> new CalculatedPropertyResult(
                        snapshot.candidatePropertyId(), 10L, 2L, BigDecimal.ONE, 3L, 4L, "test", 5L, 6L,
                        BigDecimal.ONE, scenario.equals("storage error") && snapshot.candidatePropertyId().equals(second)
                                ? "x".repeat(256) : "test", 1, "test")).toList();
                return new FinancialCalculator.Result(rows, Map.of("source", "test"), "test",
                        LocalDateTime.of(2025, 1, 1, 0, 0), 3);
            });
            var response = service.start(condition.getUser().getId(),
                    new AnalysisRunRequest(condition.getId(), List.of(first, second), "async"));
            entityManager.find(CandidateProperty.class, first).updatePropertyDetails(
                    "changed", BigDecimal.TEN, 100L, 999L, 3L, 5L);
            verifyNoInteractions(calculator);
            TestTransaction.flagForCommit();
            TestTransaction.end();
            try {
                AnalysisStatus expected = scenario.equals("success") ? AnalysisStatus.COMPLETED : AnalysisStatus.FAILED;
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(expected,
                        repository.findById(response.analysisRunId()).orElseThrow().getStatus()));
                assertEquals(scenario.equals("success") ? 2 : 0, jdbc.queryForObject(
                        "select count(*) from property_analyses where analysis_run_id = ?", Integer.class,
                        response.analysisRunId()));
                verify(calculator).calculate(anyMap(), anyList());
            } finally {
                cleanUp(response.analysisRunId(), condition.getId(), condition.getUser().getId(),
                        condition.getIndustry().getId());
            }
        }
    }

    private void cleanUp(Long runId, Long conditionId, Long userId, Long industryId) {
        TestTransaction.start();
        jdbc.update("delete from property_analyses where analysis_run_id = ?", runId);
        jdbc.update("delete from analysis_runs where id = ?", runId);
        jdbc.update("delete from candidate_properties where startup_condition_id = ?", conditionId);
        jdbc.update("delete from startup_conditions where id = ?", conditionId);
        jdbc.update("delete from users where id = ?", userId);
        jdbc.update("delete from industries where id = ?", industryId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private Long persistProperty(StartupCondition condition, String name) {
        var property = CandidateProperty.builder().startupCondition(condition).commercialAreaCode("A1")
                .name(name).address("address").latitude(37.5).longitude(127.0).areaM2(new BigDecimal("48.20"))
                .deposit(100L).monthlyRent(20L).keyMoney(3L).monthlyManagementFee(5L).build();
        entityManager.persist(property);
        return property.getId();
    }

    private StartupCondition persistCondition() {
        UUID key = UUID.randomUUID();
        User user = User.create("snapshot", key + "@example.test", null, key.getLeastSignificantBits());
        entityManager.persist(user);
        Long industryId = jdbc.queryForObject(
                "insert into industries (code, category, name) values (?, ?, ?) returning id",
                Long.class, key.toString(), "test", "snapshot");
        StartupCondition condition = StartupCondition.builder()
                .user(user).industry(entityManager.getReference(Industry.class, industryId))
                .title("snapshot condition").preferredRegionCode("11440")
                .totalCapital(100L).availableCapital(70L).loanAmount(30L)
                .monthlyLaborCost(10L).costOfSalesRate(new BigDecimal("20.00"))
                .monthlyOtherOperatingCost(5L).build();
        entityManager.persist(condition);
        return condition;
    }

    private PropertySnapshotV1 snapshot(long id, String area) {
        return new PropertySnapshotV1(1, id, "property " + id, "address", null, 37.5, 127.0,
                new BigDecimal(area), 100L, 20L, 0L, 5L, null, MappingStatus.MAPPED, "A1");
    }
}
