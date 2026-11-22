package com.irion.irion_backend.domain.analysis.repository;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/irion_dev?currentSchema=public"
})
@Transactional
class AnalysisRunRepositoryIntegrationTest {

    @Autowired private AnalysisRunRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

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
                .totalCapital(100L).availableCapital(100L).loanAmount(0L)
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
