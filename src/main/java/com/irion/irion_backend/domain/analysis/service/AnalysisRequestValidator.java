package com.irion.irion_backend.domain.analysis.service;

import com.irion.irion_backend.domain.analysis.dto.AnalysisRunRequest;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.enums.MappingStatus;
import com.irion.irion_backend.domain.property.repository.CandidatePropertyRepository;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.repository.StartupConditionRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.irion.irion_backend.domain.analysis.exception.AnalysisErrorCode.*;
import static com.irion.irion_backend.domain.property.exception.PropertyErrorCode.PROPERTY_NOT_FOUND;
import static com.irion.irion_backend.domain.startupCondition.exception.ConditionErrorCode.STARTUP_CONDITION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AnalysisRequestValidator {

    private final StartupConditionRepository conditionRepository;
    private final CandidatePropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public ValidatedRequest validate(Long requesterId, AnalysisRunRequest request) {
        if (request == null || request.startupConditionId() == null || request.candidatePropertyIds() == null) {
            throw new BusinessException(INVALID_ANALYSIS_REQUEST);
        }
        List<Long> ids = request.candidatePropertyIds();
        if (ids.isEmpty()) {
            throw new BusinessException(EMPTY_PROPERTY_LIST);
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(INVALID_ANALYSIS_REQUEST);
        }

        StartupCondition condition = conditionRepository.findById(request.startupConditionId())
                .orElseThrow(() -> new BusinessException(STARTUP_CONDITION_NOT_FOUND));
        requireOwner(condition, requesterId);

        Map<Long, CandidateProperty> byId = new HashMap<>();
        for (CandidateProperty property : propertyRepository.findAllById(ids)) {
            byId.put(property.getId(), property);
        }
        if (!byId.keySet().containsAll(ids)) {
            throw new BusinessException(PROPERTY_NOT_FOUND);
        }
        for (CandidateProperty property : byId.values()) {
            requireOwner(property.getStartupCondition(), requesterId);
        }
        for (CandidateProperty property : byId.values()) {
            if (!request.startupConditionId().equals(property.getStartupCondition().getId())) {
                throw new BusinessException(INVALID_ANALYSIS_REQUEST);
            }
        }
        for (CandidateProperty property : byId.values()) {
            if (property.getMappingStatus() != MappingStatus.MAPPED || property.getCommercialArea() == null) {
                throw new BusinessException(PROPERTY_MAPPING_NOT_COMPLETED);
            }
        }
        return new ValidatedRequest(condition, ids.stream().map(byId::get).toList());
    }

    private void requireOwner(StartupCondition condition, Long requesterId) {
        if (requesterId == null || !requesterId.equals(condition.getUser().getId())) {
            throw new BusinessException(ANALYSIS_ACCESS_DENIED);
        }
    }

    public record ValidatedRequest(StartupCondition startupCondition, List<CandidateProperty> candidateProperties) {}
}
