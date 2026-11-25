package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.calculation.CalculationPolicy;
import com.irion.irion_backend.domain.analysis.calculation.AreaAverageMonthlySales;
import com.irion.irion_backend.domain.analysis.service.AnalysisWorker;
import com.irion.irion_backend.domain.analysis.dto.AnalysisStatusResponse;
import com.irion.irion_backend.domain.analysis.dto.AnalysisHistoryResponse;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.global.error.BusinessException;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.service.AnalysisFailureService;
import com.irion.irion_backend.domain.analysis.service.AnalysisResultService;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertySnapshotV1;
import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialSalesService;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialStatisticsService;
import java.util.Optional;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.irion.irion_backend.domain.analysis.repository.AnalysisRunRepositoryIntegrationTest$HistorySqlInspector",
        "seoul-open-api.refresh-enabled=false",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class AnalysisRunRepositoryIntegrationTest {

    @Autowired private AnalysisRunRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnalysisService service;
    // Only statements on the calling thread during a history read are observed.
    public static class HistorySqlInspector implements StatementInspector {
        private static final ThreadLocal<Consumer<String>> OBSERVER = new ThreadLocal<>();

        @Override
        public String inspect(String sql) {
            var observer = OBSERVER.get();
            if (observer != null) observer.accept(sql);
            return sql;
        }

        static <T> T observe(Consumer<String> observer, Supplier<T> read) {
            OBSERVER.set(observer);
            try {
                return read.get();
            } finally {
                OBSERVER.remove();
            }
        }
    }

    private AnalysisHistoryResponse measuredHistory(Long userId, int expectedStatements) {
        entityManager.flush();
        entityManager.clear();
        var sql = new ArrayList<String>();
        var history = HistorySqlInspector.observe(sql::add, () -> service.getHistory(userId));
        assertEquals(expectedStatements, sql.size(), sql::toString);
        assertHistorySelect(sql.get(0), "users");
        assertHistorySelect(sql.get(1), "analysis_runs");
        if (expectedStatements == 3) assertHistorySelect(sql.get(2), "property_analyses");
        return history;
    }

    private static void assertHistorySelect(String sql, String table) {
        String statement = sql.stripLeading().toLowerCase(java.util.Locale.ROOT);
        org.junit.jupiter.api.Assertions.assertTrue(statement.startsWith("select ") && statement.contains(table), sql);
    }

    private Long saveHistoryRun(StartupCondition condition, List<Long> ids, String grade) {
        var runId = service.start(condition.getUser().getId(), new AnalysisRunRequest(condition.getId(), ids, "history"))
                .analysisRunId();
        var run = repository.findById(runId).orElseThrow();
        for (var snapshot : run.getPropertySnapshots()) {
            var id = snapshot.candidatePropertyId();
            var metrics = new CalculatedPropertyResult(id, 111L, 22L, BigDecimal.TEN, 33L, 44L, "ACHIEVED",
                    55L, 66L, BigDecimal.TEN, grade, ids.size() - ids.indexOf(id), "fixture");
            var property = entityManager.getReference(CandidateProperty.class, id);
            entityManager.persist(PropertyAnalysis.from(run, property, Map.of("name", snapshot.name()), metrics));
        }
        run.complete(Map.of(), "test", LocalDateTime.of(2026, 1, 1, 0, 0), null);
        entityManager.flush();
        return runId;
    }

    @Test
    void historyUsesBoundedSqlAndOnlyOwnedHistoricalSnapshots() {
        var owner = persistCondition();
        var other = persistCondition();
        Long userId = owner.getUser().getId();
        Long first = persistProperty(owner, "first");
        Long second = persistProperty(owner, "second");
        Long foreign = persistProperty(other, "foreign");
        assertEquals(List.of(), measuredHistory(userId, 2).analyses());
        var ids = List.of(second, first);
        Long newest = saveHistoryRun(owner, ids, "STABLE");
        assertEquals(newest, measuredHistory(userId, 3).analyses().get(0).analysisRunId());
        Long older = saveHistoryRun(owner, ids, "CAUTION");
        Long tied = saveHistoryRun(owner, ids, "WARNING");
        saveHistoryRun(other, List.of(foreign), "A");
        saveHistoryRun(other, List.of(foreign), "A");
        jdbc.update("update analysis_runs set created_at = timestamp '2026-01-01' where id in (?, ?)", older, tied);
        jdbc.update("update analysis_runs set created_at = timestamp '2026-01-02' where id=?", newest);
        jdbc.update("update candidate_properties set name='changed', address='changed' where startup_condition_id=?", owner.getId());

        var items = measuredHistory(userId, 3).analyses();
        assertEquals(List.of(newest, tied, older), items.stream().map(AnalysisHistoryResponse.AnalysisSummary::analysisRunId).toList());
        assertEquals(List.of("A", "C", "B"), items.stream().map(item -> item.properties().get(0).safetyGrade()).toList());
        for (var item : items) {
            assertEquals(ids, item.properties().stream().map(AnalysisHistoryResponse.PropertySummary::candidatePropertyId).toList());
            assertEquals(List.of("second", "first"), item.properties().stream().map(AnalysisHistoryResponse.PropertySummary::propertyName).toList());
            assertEquals(List.of("address", "address"), item.properties().stream().map(AnalysisHistoryResponse.PropertySummary::address).toList());
        }
        entityManager.clear();
        var missingSql = new ArrayList<String>();
        var missing = assertThrows(BusinessException.class, () -> HistorySqlInspector.observe(missingSql::add,
                () -> service.getHistory(-1L)));
        assertEquals("USER_NOT_FOUND", missing.getErrorCode().getCode());
        assertEquals(1, missingSql.size(), missingSql::toString);
        assertHistorySelect(missingSql.get(0), "users");
    }

    private void stubObservations(CommercialSalesService sales, CommercialStatisticsService statistics) {
        when(sales.latestPublishedQuarter()).thenReturn("20262");
        when(sales.findPublishedStatistics("20262", "A1", "CS999999")).thenReturn(Optional.of(new EstimatedSalesStatistics(
                "20262", "A1", "CS999999", BigDecimal.valueOf(111952232), null, null, null)));
        when(statistics.findStoreStatistics("20262", "A1", "CS999999")).thenReturn(Optional.of(new StoreStatistics(
                "20262", "A1", "CS999999", 3L, null, null, null, null, null, null)));
    }

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
        var ids = List.of(second, first);
        var response = service.start(condition.getUser().getId(), new AnalysisRunRequest(condition.getId(), ids, "run"));
        entityManager.clear();
        var run = repository.findById(response.analysisRunId()).orElseThrow();
        assertEquals(AnalysisStatus.PROCESSING, response.status());
        assertEquals(response.status(), run.getStatus());
        assertEquals("run", run.getTitle());
        assertNull(run.getAssumptionsSnapshot());
        assertEquals(ids, run.getCandidatePropertyIds());
        assertEquals(List.of("second", "first"), run.getPropertySnapshots().stream()
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
            var timestamp = jdbc.queryForObject("select completed_at from analysis_runs where id = ?",
                    LocalDateTime.class, runId);
            assertNotNull(timestamp);
            assertEquals(timestamp, completed.getCompletedAt());
            assertEquals(new AnalysisStatusResponse(runId, AnalysisStatus.COMPLETED, timestamp),
                    service.getStatus(userId, runId));
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
        void historyReadsOneSnapshotAcrossConcurrentCompletion() throws Exception {
            // This fixture's @BeforeEach has already committed PROCESSING and ended the test transaction.
            var beforeResults = new CountDownLatch(1);
            var committed = new CountDownLatch(1);
            var writer = Executors.newSingleThreadExecutor();
            try {
                var completion = writer.submit(() -> {
                    waitFor(beforeResults);
                    try {
                        complete(List.of(result(second, "A"), result(first, "B")));
                    } finally {
                        committed.countDown();
                    }
                });
                var original = HistorySqlInspector.observe(sql -> {
                    if (sql.contains("property_analyses")) {
                        assertEquals("repeatable read", jdbc.queryForObject("show transaction_isolation", String.class));
                        beforeResults.countDown();
                        waitFor(committed);
                    }
                }, () -> service.getHistory(userId)).analyses().get(0);
                completion.get(15, TimeUnit.SECONDS);
                assertEquals(AnalysisStatus.PROCESSING, original.status());
                assertNull(original.completedAt());
                assertEquals(List.of(second, first), original.properties().stream()
                        .map(AnalysisHistoryResponse.PropertySummary::candidatePropertyId).toList());
                original.properties().forEach(property -> assertNull(property.safetyGrade()));

                var latest = service.getHistory(userId).analyses().get(0);
                assertEquals(AnalysisStatus.COMPLETED, latest.status());
                assertNotNull(latest.completedAt());
                assertEquals(List.of("A", "B"), latest.properties().stream()
                        .map(AnalysisHistoryResponse.PropertySummary::safetyGrade).toList());
            } finally {
                writer.shutdownNow();
                org.junit.jupiter.api.Assertions.assertTrue(writer.awaitTermination(15, TimeUnit.SECONDS));
            }
        }

        private void waitFor(CountDownLatch latch) {
            try {
                org.junit.jupiter.api.Assertions.assertTrue(latch.await(15, TimeUnit.SECONDS), "History transaction timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        @Test
        void detailReadsCommittedSnapshotsAfterSourceChanges() {
            var runnerUp = new CalculatedPropertyResult(second, 222L, 22L, new BigDecimal("0.30"), 33L, 44L,
                    "ACHIEVED", 55L, 66L, new BigDecimal("70"), "B", 2, "second reason");
            complete(List.of(runnerUp, result(first, "A")));
            TestTransaction.start();
            try {
                jdbc.update("update startup_conditions set total_capital=999, preferred_region_code='11680' where id=?", conditionId);
                jdbc.update("update candidate_properties set name='changed', address='changed' where startup_condition_id=?", conditionId);
                entityManager.clear();
                var detail = service.getDetail(userId, runId);
                assertEquals(100L, detail.startupCondition().totalCapital());
                assertEquals("11440", detail.startupCondition().preferredRegionCode());
                assertEquals(3, detail.horizonMonths());
                assertEquals(cutoff, detail.dataCutoffAt());
                var rows = detail.properties();
                assertEquals(2, rows.size());
                assertEquals(first, rows.get(0).candidatePropertyId());
                assertEquals("first", rows.get(0).propertyName());
                assertEquals("address", rows.get(0).address());
                assertEquals(111L, rows.get(0).expectedMonthlySales());
                assertEquals(second, rows.get(1).candidatePropertyId());
                assertEquals("second", rows.get(1).propertyName());
                assertEquals("address", rows.get(1).address());
                assertEquals(222L, rows.get(1).expectedMonthlySales());
            } finally {
                TestTransaction.flagForRollback();
                TestTransaction.end();
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
    @AutoConfigureMockMvc
    class EndToEnd {
        @MockitoBean private CommercialSalesService sales;
        @MockitoBean private CommercialStatisticsService statistics;
        @Autowired private MockMvc mvc;
        @Autowired private JwtProvider jwt;
        @Autowired private ObjectMapper mapper;

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void acceptsContractAndRequiresVerifiedEstimates(boolean published) throws Exception {
            if (published) stubObservations(sales, statistics);
            var condition = persistCondition("CS999999");
            Long property = persistProperty(condition, "mapped property");
            var token = "Bearer " + jwt.createAccessToken(condition.getUser().getId()).token();
            var response = mvc.perform(post("/api/analyses").header("Authorization", token)
                            .contentType("application/json")
                            .content(mapper.writeValueAsString(new AnalysisRunRequest(condition.getId(), List.of(property), "run"))))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andReturn().getResponse();
            long runId = mapper.readTree(response.getContentAsString()).get("analysisRunId").asLong();
            TestTransaction.flagForCommit();
            TestTransaction.end();
            try {
                var expected = published ? AnalysisStatus.COMPLETED : AnalysisStatus.FAILED;
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertEquals(expected, repository.findById(runId).orElseThrow().getStatus()));
                assertEquals(published ? 1 : 0, jdbc.queryForObject("select count(*) from property_analyses where analysis_run_id = ?",
                        Integer.class, runId));
                if (published) {
                    verifyPolicyResult(runId, property);
                    var rowId = jdbc.queryForObject("select id from property_analyses where analysis_run_id = ?", Long.class, runId);
                    mvc.perform(get("/api/property-analyses/{id}", rowId).header("Authorization", token))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.propertyName").value("mapped property"))
                            .andExpect(jsonPath("$.summary.safetyScore").value(60.0))
                            .andExpect(jsonPath("$.capitalBreakdown.expectedMonthlySales").value(37_317_411));
                }
            } finally {
                cleanUp(runId, condition.getId(), condition.getUser().getId(), condition.getIndustry().getId());
            }
        }
    }

    @org.springframework.context.annotation.Primary
    static class FixturePolicy implements CalculationPolicy {
        @Override
        public Evaluation evaluate(Map<String, Object> condition, List<PropertySnapshotV1> properties) {
            return rules(condition, properties, "STABLE");
        }

        private static Evaluation rules(Map<String, Object> condition, List<PropertySnapshotV1> properties, String highGrade) {
            var method = new AreaAverageMonthlySales(4, RoundingMode.HALF_UP);
            var sales = method.estimate(condition, properties);
            long second = properties.get(1).candidatePropertyId();
            var component = new CalculationPolicy.ScoreComponent(BigDecimal.valueOf(100),
                    f -> f.id() == second ? BigDecimal.TEN : BigDecimal.ZERO);
            return new CalculationPolicy.Evaluation(sales, Set.of(CalculationPolicy.OpeningCost.DEPOSIT,
                    CalculationPolicy.OpeningCost.KEY_MONEY), List.of(component), List.of(
                    new CalculationPolicy.GradeBand(BigDecimal.TEN, highGrade, "fixture"),
                    new CalculationPolicy.GradeBand(BigDecimal.ZERO, "CAUTION", "fixture")),
                    Comparator.comparing(CalculationPolicy.Scored::score).reversed(),
                    method.assumptions(), "test", LocalDateTime.of(2025, 1, 1, 0, 0),
                    3, RoundingMode.HALF_UP);
        }
    }

    @Nested
    @ActiveProfiles("analysis-api")
    @Import(FixturePolicy.class)
    class WorkerTransactions {
        @MockitoBean private CommercialSalesService sales;
        @MockitoBean private CommercialStatisticsService statistics;
        @MockitoSpyBean private FixturePolicy policy;

        @ParameterizedTest
        @ValueSource(strings = {"success", "unreachable", "calculation error", "storage error"})
        void processesCommittedSnapshotsOnWorkerThread(String scenario) {
            stubObservations(sales, statistics);
            var condition = persistCondition("CS999999");
            Long first = persistProperty(condition, "first");
            Long second = persistProperty(condition, "second");
            if (scenario.equals("unreachable")) {
                entityManager.flush();
                jdbc.update("update startup_conditions set cost_of_sales_rate = 100 where id = ?", condition.getId());
                entityManager.refresh(condition);
            }
            var caller = Thread.currentThread();
            doAnswer(invocation -> {
                assertNotEquals(caller, Thread.currentThread());
                List<PropertySnapshotV1> snapshots = invocation.getArgument(1);
                assertEquals(List.of(first, second), snapshots.stream().map(PropertySnapshotV1::candidatePropertyId).toList());
                assertEquals(20L, snapshots.get(0).monthlyRent());
                Map<String, Object> conditionSnapshot = invocation.getArgument(0);
                assertEquals("snapshot condition", conditionSnapshot.get("title"));
                assertObservation(conditionSnapshot);
                if (scenario.equals("calculation error")) {
                    throw new IllegalStateException("test calculation failure");
                }
                if (scenario.equals("storage error")) {
                    return FixturePolicy.rules(conditionSnapshot, snapshots, "x".repeat(256));
                }
                return invocation.callRealMethod();
            }).when(policy).evaluate(anyMap(), anyList());
            var response = service.start(condition.getUser().getId(),
                    new AnalysisRunRequest(condition.getId(), List.of(first, second), "async"));
            entityManager.find(CandidateProperty.class, first).updatePropertyDetails(
                    "changed", BigDecimal.TEN, 100L, 999L, 3L, 5L);
            verifyNoInteractions(policy);
            TestTransaction.flagForCommit();
            TestTransaction.end();
            try {
                AnalysisStatus expected = scenario.endsWith("error") ? AnalysisStatus.FAILED : AnalysisStatus.COMPLETED;
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(expected,
                        repository.findById(response.analysisRunId()).orElseThrow().getStatus()));
                assertEquals(expected == AnalysisStatus.COMPLETED ? 2 : 0, jdbc.queryForObject(
                        "select count(*) from property_analyses where analysis_run_id = ?", Integer.class,
                        response.analysisRunId()));
                verify(policy).evaluate(anyMap(), anyList());
                assertObservation(repository.findById(response.analysisRunId()).orElseThrow().getConditionSnapshot());
                verify(sales).latestPublishedQuarter();
                verify(sales).findPublishedStatistics("20262", "A1", "CS999999");
                if (expected == AnalysisStatus.COMPLETED) {
                    var completed = repository.findById(response.analysisRunId()).orElseThrow();
                    assertEquals("test", completed.getCalculationVersion());
                    assertEquals(new AreaAverageMonthlySales(4, RoundingMode.HALF_UP).assumptions().get("monthlySalesEstimation"),
                            completed.getAssumptionsSnapshot().get("monthlySalesEstimation"));
                    assertEquals(List.of(37_317_411L, 37_317_411L), jdbc.queryForList(
                            "select expected_monthly_sales from property_analyses where analysis_run_id = ?",
                            Long.class, response.analysisRunId()));
                    assertEquals(List.of(2, 1), jdbc.queryForList(
                            "select recommendation_rank from property_analyses where analysis_run_id = ? order by candidate_property_id",
                            Integer.class, response.analysisRunId()));
                    if (scenario.equals("unreachable")) {
                        assertEquals(2, jdbc.queryForObject("select count(*) from property_analyses where analysis_run_id = ? " +
                                "and break_even_status = 'UNREACHABLE' and break_even_sales is null", Integer.class, response.analysisRunId()));
                    }
                    assertEquals(scenario.equals("unreachable") ? List.of(-40L, -40L) : List.of(29_853_889L, 29_853_889L), jdbc.queryForList(
                            "select monthly_operating_profit from property_analyses where analysis_run_id = ? order by candidate_property_id",
                            Long.class, response.analysisRunId()));
                }
            } finally {
                cleanUp(response.analysisRunId(), condition.getId(), condition.getUser().getId(),
                        condition.getIndustry().getId());
            }
        }
    }

    private void assertObservation(Map<String, Object> condition) {
        var snapshot = (Map<?, ?>) condition.get("seoulCommercialStatistics");
        var areas = (Map<?, ?>) snapshot.get("byCommercialAreaCode");
        var observation = (Map<?, ?>) areas.get("A1");
        assertEquals("111952232", observation.get("reportedSalesAmountWon"));
        assertEquals(3L, ((Number) observation.get("similarIndustryStoreCount")).longValue());
        assertEquals("CS999999", observation.get("industryCode"));
        assertEquals("20262", observation.get("quarterCode"));
    }

    private void verifyPolicyResult(long runId, long property) {
        var run = repository.findById(runId).orElseThrow();
        assertEquals("MVP_FINANCIAL_V1", run.getCalculationVersion());
        assertNull(run.getHorizonMonths());
        var scores = (Map<?, ?>) run.getAssumptionsSnapshot().get("stabilityByProperty");
        var details = (Map<?, ?>) scores.get(Long.toString(property));
        assertEquals(1, ((List<?>) details.get("gradeRestrictions")).size());
        assertEquals("C", jdbc.queryForObject("select safety_grade from property_analyses where analysis_run_id = ?",
                String.class, runId));
        assertEquals(37_317_411L, jdbc.queryForObject("select expected_monthly_sales from property_analyses where analysis_run_id = ?",
                Long.class, runId));
    }

    @Nested
    @ActiveProfiles("local")
    class LocalProfile {
        @Test
        void ideProfileAloneDoesNotEnableAnalysis(@Autowired ObjectProvider<CalculationPolicy> policies,
                                                  @Autowired ObjectProvider<AnalysisWorker> workers) {
            assertNull(policies.getIfAvailable());
            assertNull(workers.getIfAvailable());
        }
    }

    @Nested
    @ActiveProfiles({"local", "analysis-api"})
    @AutoConfigureMockMvc
    class PublishedStatistics {
        @Autowired private MockMvc mvc;
        @Autowired private JwtProvider jwt;
        @Autowired private ObjectMapper mapper;

        @Test
        @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_ANALYSIS_SEOUL_LIVE", matches = "true")
        void completesWithPublishedSeoulObservationInPostgres() throws Exception {
            long industry = jdbc.queryForObject("select id from industries where code = 'CS100004'", Long.class);
            var condition = persistCondition(industry);
            long property = persistProperty(condition, "published observation", "3110621");
            long funded = persistProperty(condition, "funded", "3110621");
            long partlyFunded = persistProperty(condition, "two months of fixed costs", "3110621");
            entityManager.find(CandidateProperty.class, funded).updatePropertyDetails(
                    "funded", BigDecimal.TEN, 0L, 0L, 0L, 0L);
            entityManager.find(CandidateProperty.class, partlyFunded).updatePropertyDetails(
                    "two months of fixed costs", BigDecimal.TEN, 40L, 0L, 0L, 0L);
            var ids = List.of(property, funded, partlyFunded);
            var token = jwt.createAccessToken(condition.getUser().getId()).token();
            var response = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content(mapper.writeValueAsString(new AnalysisRunRequest(condition.getId(), ids, "published observation"))))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andReturn().getResponse();
            long runId = mapper.readTree(response.getContentAsString()).get("analysisRunId").asLong();
            TestTransaction.flagForCommit();
            TestTransaction.end();
            try {
                await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                        assertEquals(AnalysisStatus.COMPLETED, repository.findById(runId).orElseThrow().getStatus()));
                var run = repository.findById(runId).orElseThrow();
                var stats = (Map<?, ?>) run.getConditionSnapshot().get("seoulCommercialStatistics");
                var areas = (Map<?, ?>) stats.get("byCommercialAreaCode");
                var observation = (Map<?, ?>) areas.get("3110621");
                assertEquals("20262", observation.get("quarterCode"));
                assertEquals("CS100004", observation.get("industryCode"));
                assertEquals("111952232", observation.get("reportedSalesAmountWon"));
                assertEquals(3L, ((Number) observation.get("similarIndustryStoreCount")).longValue());
                assertEquals(ids, run.getCandidatePropertyIds());
                assertEquals("MVP_FINANCIAL_V1", run.getCalculationVersion());
                assertNull(run.getHorizonMonths());
                var scores = (Map<?, ?>) run.getAssumptionsSnapshot().get("stabilityByProperty");
                assertEquals(Set.of("" + property, "" + funded, "" + partlyFunded), scores.keySet());
                var rows = jdbc.queryForList("""
                        select candidate_property_id, expected_monthly_sales, monthly_operating_profit,
                               break_even_sales, initial_required_cash, initial_operating_cash, safety_score,
                               safety_grade, recommendation_rank from property_analyses
                        where analysis_run_id = ? order by recommendation_rank
                        """, runId);
                assertEquals(3, rows.size());
                for (var row : rows) assertEquals(37_317_411L, row.get("expected_monthly_sales"));
                assertRankedRow(rows.get(0), funded, 1, "A", "91.11", 29_853_914L, 19L, 0L, 70L);
                assertRankedRow(rows.get(1), partlyFunded, 2, "B", "73.33", 29_853_914L, 19L, 40L, 30L);
                assertRankedRow(rows.get(2), property, 3, "C", "60.00", 29_853_889L, 50L, 103L, -33L);
            } finally {
                cleanUp(runId, condition.getId(), condition.getUser().getId(), null);
            }
        }

        private void assertRankedRow(Map<String, Object> row, long id, int rank, String grade, String score,
                                     long profit, long breakEven, long required, long remaining) {
            assertEquals(id, row.get("candidate_property_id"));
            assertEquals(rank, row.get("recommendation_rank"));
            assertEquals(grade, row.get("safety_grade"));
            assertEquals(0, new BigDecimal(score).compareTo((BigDecimal) row.get("safety_score")));
            assertEquals(profit, row.get("monthly_operating_profit"));
            assertEquals(breakEven, row.get("break_even_sales"));
            assertEquals(required, row.get("initial_required_cash"));
            assertEquals(remaining, row.get("initial_operating_cash"));
        }
    }

    private void cleanUp(Long runId, Long conditionId, Long userId, Long industryId) {
        TestTransaction.start();
        jdbc.update("delete from property_analyses where analysis_run_id = ?", runId);
        jdbc.update("delete from analysis_runs where id = ?", runId);
        jdbc.update("delete from candidate_properties where startup_condition_id = ?", conditionId);
        jdbc.update("delete from startup_conditions where id = ?", conditionId);
        jdbc.update("delete from users where id = ?", userId);
        if (industryId != null) jdbc.update("delete from industries where id = ?", industryId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private Long persistProperty(StartupCondition condition, String name) {
        return persistProperty(condition, name, "A1");
    }

    private Long persistProperty(StartupCondition condition, String name, String area) {
        var property = CandidateProperty.builder().startupCondition(condition).commercialAreaCode(area)
                .name(name).address("address").latitude(37.5).longitude(127.0).areaM2(new BigDecimal("48.20"))
                .deposit(100L).monthlyRent(20L).keyMoney(3L).monthlyManagementFee(5L).build();
        entityManager.persist(property);
        return property.getId();
    }

    private StartupCondition persistCondition() {
        return persistCondition(UUID.randomUUID().toString());
    }

    private StartupCondition persistCondition(String industryCode) {
        Long industryId = jdbc.queryForObject(
                "insert into industries (code, category, name) values (?, ?, ?) returning id",
                Long.class, industryCode, "test", "snapshot");
        return persistCondition(industryId);
    }

    private StartupCondition persistCondition(long industryId) {
        UUID key = UUID.randomUUID();
        User user = User.create("snapshot", key + "@example.test", null, key.getLeastSignificantBits());
        entityManager.persist(user);
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
