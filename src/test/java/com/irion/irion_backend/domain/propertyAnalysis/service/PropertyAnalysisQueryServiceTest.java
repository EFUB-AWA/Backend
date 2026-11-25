package com.irion.irion_backend.domain.propertyAnalysis.service;

import com.irion.irion_backend.domain.analysis.entity.AnalysisRun;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.propertyAnalysis.repository.PropertyAnalysisQueryRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PropertyAnalysisQueryServiceTest {

    @ParameterizedTest
    @CsvSource({
            "A, 안정", "B, 주의", "C, 경고",
            "STABLE, 안정", "CAUTION, 주의", "WARNING, 경고",
            "안정, 안정", "주의, 주의", "경고, 경고",
            "' a ', 안정", "' b ', 주의", "' c ', 경고", "' cAuTiOn ', 주의",
            ",", "UNKNOWN,", "'   ',"
    })
    void detailReturnsDisplayGrade(String storedGrade, String expectedGrade) {
        var repository = mock(PropertyAnalysisQueryRepository.class);
        var service = new PropertyAnalysisQueryService(repository, mock(IndustryRepository.class), mock(ObjectMapper.class));
        var analysis = mock(PropertyAnalysis.class);
        var run = mock(AnalysisRun.class);
        when(repository.findByIdAndAnalysisRun_StartupCondition_User_Id(2L, 1L)).thenReturn(Optional.of(analysis));
        when(analysis.getAnalysisRun()).thenReturn(run);
        when(run.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(analysis.getCandidateProperty()).thenReturn(mock(CandidateProperty.class));
        when(analysis.getSafetyGrade()).thenReturn(storedGrade);

        assertEquals(expectedGrade, service.getDetail(1L, 2L).summary().safetyGrade());
    }
}
