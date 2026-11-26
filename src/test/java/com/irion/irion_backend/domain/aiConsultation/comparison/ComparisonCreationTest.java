package com.irion.irion_backend.domain.aiConsultation.comparison;

import com.irion.irion_backend.domain.aiConsultation.comparison.client.ComparisonConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation.Report;
import com.irion.irion_backend.domain.aiConsultation.comparison.repository.ComparisonConsultationRepository;
import com.irion.irion_backend.domain.analysis.service.AnalysisService;
import com.irion.irion_backend.global.config.TestcontainersConfig;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import com.irion.irion_backend.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "seoul-open-api.refresh-enabled=false",
        "comparison.cleanup.enabled=false"})
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@SqlConfig(encoding = "UTF-8")
@Sql(statements = "TRUNCATE users, industries, cost_benchmarks, external_data_snapshots CASCADE")
@Sql({"/sql/property-consultation-test-data.sql", "/sql/analysis-delete-test-data.sql"})
class ComparisonCreationTest {
    static final String PATH = "/api/consultations/comparisons";
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisService analyses;
    @Autowired TransactionTemplate tx;
    @Autowired ComparisonConsultationRepository comparisons;
    @MockitoBean ComparisonConsultationAiClient ai;
    @MockitoBean TokenBlacklistRepository blacklist;

    @BeforeEach
    void setup() {
        jdbc.execute("truncate comparison_ai_consultations restart identity");
        jdbc.update("""
                update analysis_runs set completed_at=now(), data_cutoff_at=now(), calculation_version='test-v1',
                assumptions_snapshot='{}', condition_snapshot=condition_snapshot ||
                '{"industryCode":"CS100001","monthlyLaborCost":50,"costOfSalesRate":"35.0","monthlyOtherOperatingCost":15}'::jsonb
                where id in (9001,9010)
                """);
        jdbc.update("update analysis_runs set condition_snapshot=condition_snapshot || '{\"loanAmount\":200}'::jsonb where id=9010");
        jdbc.update("""
                update property_analyses set expected_monthly_sales=100,monthly_fixed_cost=10,variable_cost_rate=0.2,
                monthly_operating_profit=70,break_even_sales=null,break_even_status='UNREACHABLE',initial_required_cash=40,
                initial_operating_cash=30,safety_score=80,safety_grade='A',recommendation_rank=1,recommendation_reason='test'
                """);
        when(ai.generate(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ComparisonConsultationAiClient.Input input = call.getArgument(0);
            return report(input.differentConditions() ? "대출금 조건이 다릅니다." : null);
        });
    }

    Report report(String differences) {
        return new Report(null,"계약 보류","운영자금 확인",List.of("비용"),List.of(),List.of(),differences,"test","v1");
    }

    @Test
    void pendingSnapshotRoundTripsIsoDatesBeforeAiCompletion() throws Exception {
        var analyzed = LocalDateTime.parse("2026-11-20T14:30:15.123456");
        var cutoff = LocalDateTime.parse("2026-10-01T08:05:02.000007");
        jdbc.update("update analysis_runs set completed_at=?, data_cutoff_at=? where id=9001", analyzed, cutoff);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            tx.executeWithoutResult(s -> {
                var row = jdbc.queryForMap("""
                        select id, status, jsonb_typeof(property_snapshots #> '{0,analyzedAt}') as analyzed_type,
                               property_snapshots #>> '{0,analyzedAt}' as analyzed_text,
                               jsonb_typeof(property_snapshots #> '{0,dataCutoffAt}') as cutoff_type,
                               property_snapshots #>> '{0,dataCutoffAt}' as cutoff_text
                        from comparison_ai_consultations where comparison_key='9001:9002'
                        """);
                assertEquals("PENDING", row.get("status"));
                assertEquals("string", row.get("analyzed_type"));
                assertEquals("string", row.get("cutoff_type"));
                assertEquals(analyzed, LocalDateTime.parse((String) row.get("analyzed_text")));
                assertEquals(cutoff, LocalDateTime.parse((String) row.get("cutoff_text")));
                var stored = comparisons.findById((Long) row.get("id")).orElseThrow();
                for (var snapshot : stored.getPropertySnapshots()) {
                    assertEquals(analyzed, snapshot.analyzedAt());
                    assertEquals(cutoff, snapshot.dataCutoffAt());
                }
            });
            return report(null);
        }).when(ai).generate(any());
        submit("[9001,9002]").andExpect(status().isCreated());
        verify(ai).generate(any());
    }

    @Test
    void readsMissingNullAndPostgresGeneratedSnapshotDates() {
        var analyzed = LocalDateTime.parse("2026-11-20T14:30:15.123456");
        var cutoff = LocalDateTime.parse("2026-10-01T08:05:02.000007");
        long id = jdbc.queryForObject("""
                insert into comparison_ai_consultations(user_id, status, is_saved, created_at, updated_at, property_snapshots)
                values (9001, 'COMPLETED', true, now(), now(), jsonb_build_array(
                    jsonb_build_object('propertyId', 1),
                    jsonb_build_object('propertyId', 2, 'analyzedAt', null, 'dataCutoffAt', null),
                    jsonb_build_object('propertyId', 3, 'analyzedAt', cast(? as timestamp),
                                       'dataCutoffAt', cast(? as timestamp))))
                returning id
                """, Long.class, analyzed, cutoff);
        tx.executeWithoutResult(s -> {
            var snapshots = comparisons.findById(id).orElseThrow().getPropertySnapshots();
            assertEquals(3, snapshots.size());
            for (var legacy : snapshots.subList(0, 2)) {
                assertNull(legacy.analyzedAt());
                assertNull(legacy.dataCutoffAt());
            }
            assertEquals(analyzed, snapshots.get(2).analyzedAt());
            assertEquals(cutoff, snapshots.get(2).dataCutoffAt());
        });
    }

    ResultActions submit(String ids) throws Exception {
        return body("{\"propertyAnalysisIds\":" + ids + "}");
    }

    ResultActions body(String json) throws Exception {
        return mvc.perform(post(PATH).contentType("application/json").content(json)
                .header("Authorization","Bearer " + jwt.createAccessToken(9001L).token()));
    }

    @ParameterizedTest
    @ValueSource(strings={"null","[]","[1]","[1,2,3,4]","[1,null]","[0,2]","[-1,2]","42",
            "[\"9001\",9002]","[1.5,2]","[true,2]","[9001,9001]","[9001,9101]"})
    void invalidSelection(String ids) throws Exception {
        submit(ids).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }

    @Test
    void checksIdentityExistenceOwnershipAndReadiness() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        body("{}").andExpect(status().isBadRequest());
        submit("[9001,9999]").andExpect(status().isNotFound());
        submit("[9001,9004]").andExpect(status().isForbidden());
        for (String state : List.of("PROCESSING","FAILED")) {
            jdbc.update("update analysis_runs set status=? where id=9001",state);
            submit("[9001,9002]").andExpect(status().isConflict());
        }
        jdbc.update("update analysis_runs set status='COMPLETED' where id=9001");
        jdbc.update("update property_analyses set monthly_fixed_cost=null where id=9001");
        submit("[9001,9002]").andExpect(status().isConflict());
        verifyNoInteractions(ai);
    }

    @Test
    void preservesOrderAndHistoricalMetricsAcrossRuns() throws Exception {
        jdbc.update("update candidate_properties set name='changed', monthly_rent=999 where id=9001");
        jdbc.update("update startup_conditions set loan_amount=999 where id=9001");
        submit("[9102,9001,9003]").andExpect(status().isCreated()).andExpect(jsonPath("$.isSaved").value(false))
                .andExpect(jsonPath("$.analysisRunId").doesNotExist())
                .andExpect(jsonPath("$.properties[0].analysisRunId").value(9010))
                .andExpect(jsonPath("$.properties[1].name").value("이대 정문 1층"))
                .andExpect(jsonPath("$.properties[1].financialResult.monthlyFixedCost").value(10))
                .andExpect(jsonPath("$.properties[1].financialResult.initialOperatingCash").value(30))
                .andExpect(jsonPath("$.properties[1].financialResult.breakEvenSales").isEmpty())
                .andExpect(jsonPath("$.conditionDifferences").isString());
        String sql = "select expires_at from comparison_ai_consultations where comparison_key='9001:9003:9102'";
        var expires = jdbc.queryForObject(sql,LocalDateTime.class);
        submit("[9003,9001,9102]").andExpect(status().isOk()).andExpect(jsonPath("$.properties[0].propertyAnalysisId").value(9102));
        assertEquals(expires,jdbc.queryForObject(sql,LocalDateTime.class));
        assertEquals(Boolean.TRUE,jdbc.queryForObject("""
                select expires_at=completed_at+interval '24 hours' and property_snapshots #>> '{1,conditionSnapshot,loanAmount}'='0'
                and property_snapshots #>> '{1,calculationVersion}'='test-v1'
                from comparison_ai_consultations
                """,Boolean.class));
        submit("[9001,9003]").andExpect(status().isCreated());
        verify(ai,times(2)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(ints={500,502,504})
    void committedFailureCanBeReused(int httpStatus) throws Exception {
        var code = switch(httpStatus) {
            case 500 -> JSON_PARSING_FAILED;
            case 502 -> AI_CONSULTATION_GENERATION_FAILED;
            default -> AI_CONSULTATION_TIMEOUT;
        };
        doThrow(new BusinessException(code)).when(ai).generate(any());
        submit("[9001,9002]").andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.data.comparisonConsultId").isNumber()).andExpect(jsonPath("$.data.status").value("FAILED"));
        submit("[9002,9001]").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        verify(ai).generate(any());
    }

    @Test
    void pendingReservationSurvivesSourceDeletionDuringAi() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            assertTrue(release.await(10,TimeUnit.SECONDS));
            return report(null);
        }).when(ai).generate(any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> submit("[9102,9001]").andReturn());
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            submit("[9001,9102]").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
            analyses.delete(9001L,9001L);
            release.countDown();
            assertEquals(201,first.get(5,TimeUnit.SECONDS).getResponse().getStatus());
            submit("[9001,9102]").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
            submit("[9001,9103]").andExpect(status().isNotFound());
            assertEquals(1,comparisons.count());
            verify(ai).generate(any());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredCombinationHasOneWinnerWhenRequestsQueueForTheSameRun() throws Exception {
        submit("[9001,9102]").andExpect(status().isCreated());
        jdbc.update("update comparison_ai_consultations set expires_at=now()-interval '1 second'");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var queued = tx.execute(s -> {
                jdbc.queryForObject("select id from analysis_runs where id=9001 for update",Long.class);
                var first = pool.submit(() -> submit("[9102,9001]").andReturn().getResponse().getStatus());
                var second = pool.submit(() -> submit("[9001,9102]").andReturn().getResponse().getStatus());
                awaitWaiters(2);
                return List.of(first,second);
            });
            var statuses = List.of(queued.get(0).get(10,TimeUnit.SECONDS),queued.get(1).get(10,TimeUnit.SECONDS));
            assertTrue(statuses.containsAll(List.of(200,201)));
            assertEquals(1,comparisons.count());
            verify(ai,times(2)).generate(any());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void overlappingDistinctCombinationsCanBothReserve() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var queued = tx.execute(s -> {
                jdbc.queryForObject("select id from analysis_runs where id=9001 for update",Long.class);
                var first = pool.submit(() -> submit("[9102,9001]").andReturn().getResponse().getStatus());
                var second = pool.submit(() -> submit("[9002,9103]").andReturn().getResponse().getStatus());
                awaitWaiters(2);
                return List.of(first,second);
            });
            for (var request : queued) assertEquals(201,request.get(10,TimeUnit.SECONDS));
            verify(ai,times(2)).generate(any());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sourceDeletionCommitsAheadOfWaitingReservation() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            var queued = tx.execute(s -> {
                jdbc.queryForObject("select id from analysis_runs where id=9001 for update",Long.class);
                var request = pool.submit(() -> submit("[9001,9102]").andReturn().getResponse().getStatus());
                awaitWaiters(1);
                analyses.delete(9001L,9001L);
                return request;
            });
            assertEquals(404,queued.get(10,TimeUnit.SECONDS));
            verifyNoInteractions(ai);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void savedRowSurvivesConcurrentExpiration() throws Exception {
        submit("[9001,9002]").andExpect(status().isCreated());
        var before = jdbc.queryForObject("select completed_at from comparison_ai_consultations",LocalDateTime.class);
        var id = jdbc.queryForObject("select id from comparison_ai_consultations",Long.class);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var cleanup = tx.execute(s -> {
                assertEquals(1,comparisons.updateSaved(id,before,
                        com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus.COMPLETED));
                var request = pool.submit(() -> tx.execute(other -> comparisons.deleteExpired(before.plusDays(1))));
                awaitWaiters(1);
                return request;
            });
            assertEquals(0,cleanup.get(10,TimeUnit.SECONDS));
            assertEquals(before,comparisons.findById(id).orElseThrow().getSavedAt());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void committedUniqueWinnerIsReadAfterReservationRollback() throws Exception {
        submit("[9001,9002]").andExpect(status().isCreated());
        long winner = jdbc.queryForObject("select id from comparison_ai_consultations", Long.class);
        jdbc.update("update comparison_ai_consultations set comparison_key=null where id=?", winner);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var waiting = tx.execute(s -> {
                // An independent writer owns the unique index entry, invisible to reservation SELECTs until commit.
                jdbc.update("update comparison_ai_consultations set comparison_key='9001:9002' where id=?", winner);
                var request = pool.submit(() -> submit("[9002,9001]").andExpect(status().isOk())
                        .andExpect(jsonPath("$.comparisonConsultId").value(winner)).andReturn());
                awaitWaiters(1);
                return request;
            });
            waiting.get(10,TimeUnit.SECONDS);
            assertEquals(1,comparisons.count());
            verify(ai).generate(any());
        } finally {
            pool.shutdownNow();
        }

        jdbc.queryForObject("select setval(pg_get_serial_sequence('comparison_ai_consultations','id'),?,false)",
                Long.class,winner);
        submit("[9001,9003]").andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").doesNotExist());
        assertEquals(1,comparisons.count());
        verify(ai).generate(any());
    }

    @Test
    void runtimeFailureReturnsCommittedId() throws Exception {
        doThrow(new IllegalStateException("private payload")).when(ai).generate(any());
        var response = submit("[9001,9002]").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.status").value("FAILED")).andReturn().getResponse();
        long id = jdbc.queryForObject("select id from comparison_ai_consultations where status='FAILED'",Long.class);
        var json = new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(id,json.path("data").path("comparisonConsultId").longValue());
        assertFalse(response.getContentAsString().contains("private payload"));
        submit("[9002,9001]").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        verify(ai).generate(any());
    }

    @Test
    void comparesHistoricalIndustryUsingTheSelectedFrozenArea() throws Exception {
        jdbc.update("""
                update analysis_runs set condition_snapshot=(condition_snapshot-'industryCode') ||
                    '{"seoulCommercialStatistics":{"byCommercialAreaCode":{"A":{"commercialAreaCode":"A","industryCode":"CS100001"},
                    "B":{"commercialAreaCode":"B","industryCode":"CS100002"}}}}'::jsonb,
                    property_snapshots=jsonb_set(property_snapshots,'{0,commercialAreaCode}','"A"') where id=9001
                """);
        jdbc.update("update analysis_runs set condition_snapshot=jsonb_set(condition_snapshot,'{loanAmount}','0') where id=9010");
        submit("[9001,9102]").andExpect(status().isCreated()).andExpect(jsonPath("$.conditionDifferences").isEmpty());
        assertEquals("CS100001",jdbc.queryForObject("""
                select property_snapshots #>> '{0,conditionSnapshot,industryCode}' from comparison_ai_consultations
                """,String.class));
        jdbc.update("""
                update analysis_runs set condition_snapshot=jsonb_set(condition_snapshot,'{industryCode}','"CS100002"')
                where id=9001
                """);
        submit("[9001,9103]").andExpect(status().isConflict());
        jdbc.update("""
                update analysis_runs set condition_snapshot=(condition_snapshot-'seoulCommercialStatistics') ||
                    '{"industryCode":"CS100002","industryId":2}'::jsonb where id=9001
                """);
        submit("[9001,9103]").andExpect(status().isCreated()).andExpect(jsonPath("$.conditionDifferences").isString());
        jdbc.update("""
                update analysis_runs set condition_snapshot=(condition_snapshot-'industryCode') || '{"industryId":1}'::jsonb
                where id=9001
                """);
        submit("[9002,9103]").andExpect(status().isCreated()).andExpect(jsonPath("$.conditionDifferences").isEmpty());
        jdbc.update("""
                update analysis_runs set condition_snapshot=jsonb_set(condition_snapshot,'{totalCapital}','{"email":"hidden"}')
                where id=9001
                """);
        submit("[9002,9003]").andExpect(status().isConflict());
    }

    void awaitWaiters(int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if (jdbc.queryForObject("""
                    with recursive blocked(pid) as (
                        select pg_backend_pid()
                        union
                        select activity.pid from pg_stat_activity activity join blocked b
                            on b.pid = any(pg_blocking_pids(activity.pid))
                    ) select count(*) from blocked where pid <> pg_backend_pid()
                    """,Integer.class) >= count) return;
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        } while (System.nanoTime() < deadline);
        fail("PostgreSQL did not observe the expected lock waiters");
    }

    @Test
    void recoveryBoundaryAndLateCompletion() throws Exception {
        var recovery = LocalDateTime.of(2026,6,1,12,0);
        doAnswer(call -> {
            jdbc.update("update comparison_ai_consultations set created_at=?",recovery.minusHours(1));
            tx.executeWithoutResult(s -> {
                assertEquals(0,comparisons.deleteExpired(recovery));
                assertEquals(0,comparisons.recoverPending(recovery.minusHours(1),recovery,recovery.plusDays(1)));
                var later = recovery.plusNanos(1000);
                assertEquals(1,comparisons.recoverPending(later.minusHours(1),later,later.plusDays(1)));
                assertEquals(0,comparisons.deleteExpired(later.plusDays(1).minusNanos(1000)));
            });
            return report(null);
        }).when(ai).generate(any());
        submit("[9001,9002]").andExpect(status().isConflict());
        assertEquals("FAILED",jdbc.queryForObject("select status from comparison_ai_consultations",String.class));
        tx.executeWithoutResult(s -> assertEquals(1,comparisons.deleteExpired(recovery.plusDays(1).plusNanos(1000))));
    }
}
