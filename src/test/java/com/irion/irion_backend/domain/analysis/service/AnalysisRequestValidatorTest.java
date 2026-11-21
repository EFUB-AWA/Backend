package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.commercialArea.entity.CommercialArea;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.property.repository.CandidatePropertyRepository;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.repository.StartupConditionRepository;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.*;
import static com.irion.irion_backend.domain.property.exception.PropertyErrorCode.PROPERTY_NOT_FOUND;
import static com.irion.irion_backend.domain.startupCondition.exception.StartupConditionErrorCode.STARTUP_CONDITION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisRequestValidatorTest {

    private final StartupConditionRepository conditions = mock(StartupConditionRepository.class);
    private final CandidatePropertyRepository properties = mock(CandidatePropertyRepository.class);
    private final AnalysisRequestValidator validator = new AnalysisRequestValidator(conditions, properties);

    private AnalysisRunRequest request(Long... ids) {
        return new AnalysisRunRequest(1L, Arrays.asList(ids), null);
    }

    private void expectError(AnalysisRunRequest request, ErrorCode code) {
        BusinessException error = assertThrows(BusinessException.class, () -> validator.validate(7L, request));
        assertSame(code, error.getErrorCode());
    }

    private StartupCondition condition(long id, long ownerId) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        StartupCondition condition = mock(StartupCondition.class);
        when(condition.getId()).thenReturn(id);
        when(condition.getUser()).thenReturn(owner);
        when(conditions.findById(id)).thenReturn(Optional.of(condition));
        return condition;
    }

    private CandidateProperty property(long id, StartupCondition condition, MappingStatus status) {
        CandidateProperty property = mock(CandidateProperty.class);
        when(property.getId()).thenReturn(id);
        when(property.getStartupCondition()).thenReturn(condition);
        when(property.getMappingStatus()).thenReturn(status);
        return property;
    }

    @Test
    void rejectsMalformedRequestsBeforeRepositoryAccess() {
        expectError(null, INVALID_ANALYSIS_REQUEST);
        expectError(new AnalysisRunRequest(null, List.of(), null), INVALID_ANALYSIS_REQUEST);
        expectError(new AnalysisRunRequest(1L, null, null), INVALID_ANALYSIS_REQUEST);
        expectError(request(), EMPTY_PROPERTY_LIST);
        expectError(request(10L, null), INVALID_ANALYSIS_REQUEST);
        verifyNoInteractions(conditions, properties);
    }

    @Test
    void missingCondition() {
        expectError(request(10L), STARTUP_CONDITION_NOT_FOUND);
        verifyNoInteractions(properties);
    }

    @Test
    void foreignCondition() {
        condition(1, 8);
        expectError(request(10L), ANALYSIS_ACCESS_DENIED);
        verifyNoInteractions(properties);
    }

    @Test
    void missingPropertyTakesPrecedenceOverMappingError() {
        CandidateProperty first = property(10, condition(1, 7), MappingStatus.FAILED);
        when(properties.findAllById(List.of(10L, 11L))).thenReturn(List.of(first));
        expectError(request(10L, 11L), PROPERTY_NOT_FOUND);
    }

    @Test
    void foreignProperty() {
        condition(1, 7);
        CandidateProperty foreign = property(10, condition(2, 8), MappingStatus.FAILED);
        when(properties.findAllById(List.of(10L))).thenReturn(List.of(foreign));
        expectError(request(10L), ANALYSIS_ACCESS_DENIED);
    }

    @Test
    void otherConditionOfSameOwner() {
        condition(1, 7);
        CandidateProperty other = property(10, condition(2, 7), MappingStatus.MAPPED);
        when(properties.findAllById(List.of(10L))).thenReturn(List.of(other));
        expectError(request(10L), INVALID_ANALYSIS_REQUEST);
    }

    @Test
    void incompleteMapping() {
        CandidateProperty property = property(10, condition(1, 7), MappingStatus.FAILED);
        when(property.getCommercialAreaCode()).thenReturn("3110008");
        when(properties.findAllById(List.of(10L))).thenReturn(List.of(property));

        expectError(request(10L), PROPERTY_MAPPING_NOT_COMPLETED);
    }

    @Test
    void mappedWithoutArea() {
        CandidateProperty property = property(10, condition(1, 7), MappingStatus.MAPPED);
        when(properties.findAllById(List.of(10L))).thenReturn(List.of(property));
        expectError(request(10L), PROPERTY_MAPPING_NOT_COMPLETED);
    }

    @Test
    void returnsMappedPropertiesInRequestOrderIncludingRepeatedIds() {
        StartupCondition condition = condition(1, 7);
        CandidateProperty a = property(10, condition, MappingStatus.MAPPED);
        CandidateProperty b = property(11, condition, MappingStatus.MAPPED);
        when(a.getCommercialAreaCode()).thenReturn("3110008");
        when(b.getCommercialAreaCode()).thenReturn("3110008");
        AnalysisRunRequest request = request(11L, 10L, 11L);
        when(properties.findAllById(request.candidatePropertyIds())).thenReturn(List.of(a, b));

        var result = validator.validate(7L, request);

        assertSame(condition, result.startupCondition());
        assertEquals(List.of(b, a, b), result.candidateProperties());
        verify(properties).findAllById(request.candidatePropertyIds());
    }
}
