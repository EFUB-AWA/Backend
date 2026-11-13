package com.irion.irion_backend.domain.property.service;

import com.irion.irion_backend.domain.property.dto.request.PropertyCreateRequest;
import com.irion.irion_backend.domain.property.dto.response.PropertyCreateResponse;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.repository.CandidatePropertyRepository;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.service.ConditionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyService {

    private final CandidatePropertyRepository propertyRepository;
    private final ConditionService conditionService;

    @Transactional
    public PropertyCreateResponse registerProperty(Long requesterId, PropertyCreateRequest request) {

        StartupCondition condition = conditionService.findOwnedCondition(request.startupConditionId(), requesterId);
        CandidateProperty property = request.toEntity(condition);
        CandidateProperty saved = propertyRepository.save(property);

        return PropertyCreateResponse.from(saved);
    }
}
