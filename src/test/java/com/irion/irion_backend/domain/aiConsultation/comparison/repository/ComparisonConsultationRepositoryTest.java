package com.irion.irion_backend.domain.aiConsultation.comparison.repository;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import com.irion.irion_backend.global.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql({"/sql/property-consultation-test-data.sql", "/sql/comparison-consultation-test-data.sql"})
public class ComparisonConsultationRepositoryTest {

    @Autowired
    private ComparisonConsultationRepository comparisonConsultationRepository;

    private static final Long OWNER_ID = 9001L;
    private static final Long COMPLETED_CONSULTATION_ID = 9001L;

    @Test
    @DisplayName("COMPLETED이면 jsonb 후보매물 리스트가 조회된다.")
    void findOwnedById_mapsJsonbColumns() {
        // when
        ComparisonConsultation consultation = comparisonConsultationRepository
                .findOwnedById(COMPLETED_CONSULTATION_ID, OWNER_ID).orElseThrow();
        PropertySnapshot snapshot = consultation.getPropertySnapshots().get(0);

        // then
        assertThat(consultation.getPropertySnapshots())
                .hasSize(3)
                .extracting(PropertySnapshot::propertyId)
                .containsExactly(9001L, 9002L, 9003L);
        assertThat(snapshot.deposit()).isEqualTo(30000000L);
        assertThat(snapshot.areaM2()).isEqualByComparingTo("66.12");
    }

    @Test
    @DisplayName("PENDING이면 조회 시 내용 컬럼들이 null이다.")
    void findOwnedById_pending_contents_null() {
        // when
        ComparisonConsultation consultation = comparisonConsultationRepository
                .findOwnedById(9004L, OWNER_ID).orElseThrow();

        // then
        assertThat(consultation.getPropertySnapshots())
                .hasSize(2);
        assertThat(consultation.getSummary()).isNull();
        assertThat(consultation.getDescription()).isNull();
        assertThat(consultation.getProsAndCons()).isNull();

    }

    @Test
    @DisplayName("다른 회원 소유의 컨설팅을 조회하면 빈 값이 반환된다.")
    void findOwnedById_other_return_empty() {
        // when & then
        assertThat(comparisonConsultationRepository
                .findOwnedById(9006L, OWNER_ID)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 컨설팅을 조회하면 빈 값이 반환된다.")
    void findOwnedById_not_exist() {
        // when & then
        assertThat(comparisonConsultationRepository
                .findOwnedById(99999L, OWNER_ID)).isEmpty();
    }

    @Test
    @DisplayName("첫 페이지는 저장한 내 컨설팅을 id 내림차순으로 조회한다.")
    void findSavedFirstPage_get_consultations() {
        // when
        List<ComparisonConsultation> consultationList = comparisonConsultationRepository
                .findSavedFirstPage(OWNER_ID, PageRequest.of(0, 10));

        // then
        assertThat(consultationList)
                .hasSize(5)
                .extracting(ComparisonConsultation::getId)
                .containsExactly(9010L, 9009L, 9008L, 9007L, 9001L);
    }

    @Test
    @DisplayName("첫 페이지는 개수 제한이 적용된다.")
    void findSavedFirstPage_get_list_of_size() {
        // when
        List<ComparisonConsultation> consultationList = comparisonConsultationRepository
                .findSavedFirstPage(OWNER_ID, PageRequest.of(0, 3));

        // then
        assertThat(consultationList)
                .hasSize(3)
                .extracting(ComparisonConsultation::getId)
                .containsExactly(9010L, 9009L, 9008L);
    }

    @Test
    @DisplayName("다음 페이지는 커서보다 작은 id만 조회한다.")
    void findSavedNextPage_get_id_less_than_cursor() {
        // when
        List<ComparisonConsultation> consultationList = comparisonConsultationRepository
                .findSavedNextPage(OWNER_ID, 9009L, PageRequest.of(0, 10));

        // then
        assertThat(consultationList)
                .extracting(ComparisonConsultation::getId)
                .containsExactly(9008L, 9007L, 9001L);
    }

    @Test
    @DisplayName("다른 회원은 내 저장본을 조회하지 못한다.")
    void findSavedFirstPage_other_cannot_see_mine() {
        // when
        List<ComparisonConsultation> consultationList = comparisonConsultationRepository
                .findSavedFirstPage(9002L, PageRequest.of(0, 10));

        // then
        assertThat(consultationList)
                .extracting(ComparisonConsultation::getId)
                .containsExactly(9006L);
    }
}
