package com.irion.irion_backend.domain.aiConsultation.comparison.dto.response;


import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ComparisonConsultationListResponseTest {

    @Test
    @DisplayName("조회 결과가 size보다 많으면 size개만 내려주고, 마지막 항목 id를 커서로 반환한다.")
    void of_hasNext() {
        // given: size + 1개 조회
        List<ComparisonConsultation> fetched = List.of(consultation(5L), consultation(4L), consultation(3L));

        // when
        ComparisonConsultationListResponse response = ComparisonConsultationListResponse.of(fetched, 2);

        // then
        assertThat(response.consultations())
                .hasSize(2)
                .extracting(ComparisonConsultationSummaryResponse::comparisonConsultId)
                .containsExactly(5L, 4L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(4L);
    }

    @Test
    @DisplayName("조회 결과가 size와 같으면 다음 페이지는 false이다.")
    void of_hasNext_false() {
        // given: size개 조회
        List<ComparisonConsultation> fetched = List.of(consultation(5L), consultation(4L), consultation(3L));

        // when
        ComparisonConsultationListResponse response = ComparisonConsultationListResponse.of(fetched, 3);

        // then
        assertThat(response.consultations())
                .hasSize(3)
                .extracting(ComparisonConsultationSummaryResponse::comparisonConsultId)
                .containsExactly(5L, 4L, 3L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("조회 결과가 비어있으면 빈 목록이 반환된다.")
    void of_empty() {
        // given: 빈 리스트
        List<ComparisonConsultation> fetched = List.of();

        // when
        ComparisonConsultationListResponse response = ComparisonConsultationListResponse.of(fetched, 10);

        // then
        assertThat(response.consultations()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }


    // ----- helper -----
    private ComparisonConsultation consultation(Long id) {
        ComparisonConsultation consultation = mock(ComparisonConsultation.class);
        AnalysisRun analysisRun = mock(AnalysisRun.class);

        when(consultation.getId()).thenReturn(id);
        when(consultation.getAnalysisRun()).thenReturn(analysisRun);
        when(analysisRun.getId()).thenReturn(id);

        return consultation;
    }

}
