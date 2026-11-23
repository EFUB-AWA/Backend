package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
