package com.irion.irion_backend.domain.aiConsultation.comparison.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ComparisonMigrationTest {
    @Container
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
    private String migration;

    @BeforeEach
    void createLegacySchema() throws Exception {
        migration = Files.readString(Path.of("db/manual/04-decouple-comparison-consultations.sql"));
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            sql.execute(Files.readString(Path.of("src/test/resources/sql/comparison-legacy-schema.sql")));
            sql.execute("""
                    INSERT INTO users (id,login_email,nickname,kakao_id,created_at,updated_at)
                        VALUES (1,'fixture@example.test','fixture',1,now(),now());
                    INSERT INTO industries (id,code,category,name) VALUES (1,'test','test','test');
                    INSERT INTO startup_conditions (id,user_id,industry_id,title,preferred_region_code,total_capital,
                        available_capital,loan_amount,monthly_labor_cost,cost_of_sales_rate,monthly_other_operating_cost,
                        created_at,updated_at) VALUES (2,1,1,'test','test',100,100,0,0,0,0,now(),now());
                    INSERT INTO candidate_properties (id,startup_condition_id,name,address,area_m2,deposit,key_money,
                        latitude,longitude,mapping_status,monthly_management_fee,monthly_rent,created_at,updated_at)
                        SELECT id,2,'test','test',10,0,0,37,127,'MAPPED',0,0,now(),now() FROM (VALUES (10),(20)) p(id);
                    INSERT INTO analysis_runs (id,startup_condition_id,status,candidate_property_ids,condition_snapshot,
                        property_snapshots,created_at,updated_at)
                        SELECT id,2,'COMPLETED',ids,'{}',snapshots,now(),now() FROM (VALUES
                            (3,'[20,10]'::jsonb,'[{"candidatePropertyId":20},{"candidatePropertyId":10}]'::jsonb),
                            (4,'[20]'::jsonb,'[{"candidatePropertyId":20}]'::jsonb)) r(id,ids,snapshots);
                    INSERT INTO property_analyses (analysis_run_id,candidate_property_id,expected_monthly_sales,
                        monthly_operating_profit,break_even_sales,break_even_status,initial_required_cash,initial_operating_cash,
                        safety_score,safety_grade,recommendation_rank,recommendation_reason,property_snapshot,created_at,updated_at)
                        SELECT p.*,'{}'::jsonb,now(),now() FROM (VALUES
                        (3,10,100,-5,NULL,'UNREACHABLE',70,30,23.5,'D',2,'risk'),
                        (3,20,150,50,90,'REACHABLE',60,40,82,'A',1,'profit'),
                        (4,20,200,70,95,'REACHABLE',80,45,85,'A',1,'later')) p;
                    INSERT INTO comparison_ai_consultations (id,analysis_run_id,property_snapshots,is_saved,summary,saved_at,
                        expires_at,status,created_at,updated_at,description,pros_and_cons,model_name,prompt_version,completed_at) VALUES
                        (5,3,'[{"propertyId":20,"name":"old"},{"propertyId":10}]',true,'saved','2025-01-01',NULL,'COMPLETED',now(),now(),'description','pros','model','v1',now()),
                        (6,4,'[{"propertyId":20}]',false,'temporary',NULL,'2025-01-02','COMPLETED',now(),now(),'detail','cons','model','v2',now());
                    """);
        }
    }

    @Test
    void actualScriptPreservesEachRunAndBothKindsOfHistory() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            String metadata = scalar(sql, "SELECT jsonb_agg(to_jsonb(c) - 'property_snapshots' ORDER BY id) FROM comparison_ai_consultations c");
            sql.execute(migration);
            sql.execute("DELETE FROM property_analyses WHERE analysis_run_id IN (3,4)");
            sql.execute("DELETE FROM analysis_runs WHERE id IN (3,4)");
            assertThat(scalar(sql, "SELECT jsonb_agg(to_jsonb(c) - 'property_snapshots' - 'user_id' ORDER BY id) FROM comparison_ai_consultations c"))
                    .isEqualTo(metadata);
            assertThat(scalar(sql, "SELECT count(*) FROM comparison_ai_consultations WHERE user_id = 1")).isEqualTo("2");
            assertThat(scalar(sql, """
                    SELECT property_snapshots @> '[{"propertyId":10,"financialResult":{"breakEvenSales":null,
                        "breakEvenStatus":"UNREACHABLE","recommendationRank":2,"recommendationReason":"risk",
                        "monthlyOperatingProfit":-5,"safetyScore":23.5,"safetyGrade":"D","expectedMonthlySales":100,
                        "initialRequiredCash":70,"initialOperatingCash":30}}]'
                        AND property_snapshots #>> '{0,name}' = 'old'
                        AND property_snapshots #>> '{0,financialResult,expectedMonthlySales}' = '150'
                    FROM comparison_ai_consultations WHERE id = 5
                    """)).isEqualTo("t");
            assertThat(scalar(sql, "SELECT property_snapshots #>> '{0,financialResult,expectedMonthlySales}' FROM comparison_ai_consultations WHERE id=6"))
                    .isEqualTo("200");
            sql.execute("DELETE FROM candidate_properties WHERE startup_condition_id=2");
            sql.execute("DELETE FROM startup_conditions WHERE id=2");
            assertThatThrownBy(() -> sql.execute("DELETE FROM users WHERE id=1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("fk_comparison_user");
            assertThatThrownBy(() -> sql.execute("UPDATE comparison_ai_consultations SET analysis_run_id=3 WHERE id=6"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("unique");
        }
    }

    @Test
    void incompleteSourceRollsBackSchemaAndSnapshots() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("UPDATE property_analyses SET recommendation_reason=NULL WHERE analysis_run_id=4");
            String before = scalar(sql, "SELECT jsonb_agg(to_jsonb(c) ORDER BY id) FROM comparison_ai_consultations c");
            assertThatThrownBy(() -> sql.execute(migration)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("Incomplete or conflicting financial result");
            sql.execute("ROLLBACK");
            assertThat(scalar(sql, "SELECT jsonb_agg(to_jsonb(c) ORDER BY id) FROM comparison_ai_consultations c")).isEqualTo(before);
            assertThat(scalar(sql, "SELECT count(*) FROM information_schema.columns WHERE table_name='comparison_ai_consultations' AND column_name='user_id'"))
                    .isEqualTo("0");
            assertThat(scalar(sql, """
                    SELECT count(*) FROM pg_constraint WHERE contype='f' AND conrelid='comparison_ai_consultations'::regclass
                        AND confrelid='analysis_runs'::regclass
                    """)).isEqualTo("1");
        }
    }

    private String scalar(Statement sql, String query) throws SQLException {
        try (var result = sql.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
