package com.irion.irion_backend.domain.aiConsultation.property.repository;

import com.irion.irion_backend.domain.aiConsultation.property.entity.ImprovementSuggestion;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.property.entity.RiskFactor;
import com.irion.irion_backend.domain.aiConsultation.property.enums.RiskLevel;
import com.irion.irion_backend.global.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/property-consultation-test-data.sql")
public class PropertyConsultationRepositoryTest {

    @Autowired
    private PropertyConsultationRepository propertyConsultationRepository;

    private static final Long OWNER_ID = 9001L;
    private static final Long COMPLETED_CONSULTATION_ID = 9001L;

    @Test
    @DisplayName("COMPLETED 컨설팅의 jsonb 위험 요인과 개선 방안을 리스트로 조회한다.")
    void findOwnedById_mapsJsonbColumns() {
        // given: SQL로 위험 요인 2개, 개선 방안 4개 저장

        // when
        PropertyConsultation consultation = propertyConsultationRepository
                .findOwnedById(COMPLETED_CONSULTATION_ID, OWNER_ID).orElseThrow();

        // then
        assertThat(consultation.getRiskFactors())
                .hasSize(2)
                .first()
                .extracting(RiskFactor::level)
                .isEqualTo(RiskLevel.HIGH);

        assertThat(consultation.getImprovementSuggestions())
                .hasSize(4)
                .extracting(ImprovementSuggestion::priority)
                .containsExactly(3, null, 1, 2);
    }

    @Test
    @DisplayName("다른 회원 컨설팅은 조회되지 않는다.")
    void findOwnedById_otherUsersConsultation() {
        // when & then
        assertThat(propertyConsultationRepository.findOwnedById(9004L, OWNER_ID)).isEmpty();

    }

    @Test
    @DisplayName("존재하지 않는 id는 빈 결과를 반환한다.")
    void findOwnedById_notExists() {
        // when & then
        assertThat(
                propertyConsultationRepository.findOwnedById(99999L, OWNER_ID)
        ).isEmpty();
    }

    @Test
    @DisplayName("PENDING 컨설팅은 jsonb 컬럼이 null로 조회된다.")
    void findOwnedById_pendingHasNullJsonb() {
        // when
        PropertyConsultation consultation = propertyConsultationRepository
                .findOwnedById(9002L, OWNER_ID).orElseThrow();

        // then
        assertThat(consultation.getRiskFactors())
                .isNull();

    }
}
