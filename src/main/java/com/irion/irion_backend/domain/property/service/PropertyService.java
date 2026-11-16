package com.irion.irion_backend.domain.property.service;

import com.irion.irion_backend.domain.property.dto.request.PropertyCreateRequest;
import com.irion.irion_backend.domain.property.dto.request.PropertyUpdateRequest;
import com.irion.irion_backend.domain.property.dto.response.PropertyCreateResponse;
import com.irion.irion_backend.domain.property.dto.response.PropertyDetailResponse;
import com.irion.irion_backend.domain.property.dto.response.PropertyListResponse;
import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.property.exception.PropertyErrorCode;
import com.irion.irion_backend.domain.property.repository.CandidatePropertyRepository;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import com.irion.irion_backend.domain.startupCondition.service.ConditionService;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyService {

    private final CandidatePropertyRepository propertyRepository;
    private final ConditionService conditionService;

    // 후보 매물 등록
    @Transactional
    public PropertyCreateResponse registerProperty(Long requesterId, PropertyCreateRequest request) {

        StartupCondition condition = conditionService.findOwnedCondition(request.startupConditionId(), requesterId);
        CandidateProperty property = request.toEntity(condition);
        CandidateProperty saved = propertyRepository.save(property);

        return PropertyCreateResponse.from(saved);
    }

    // 후보 매물 목록 조회
    public PropertyListResponse getProperties(Long requesterId, Long conditionId) {

        // 요청자의 창업 조건인지 확인
        StartupCondition condition = conditionService.findOwnedCondition(conditionId, requesterId);

        List<CandidateProperty> properties = propertyRepository.findByStartupConditionOrderByCreatedAtDesc(condition);
        return PropertyListResponse.from(properties);
    }

    // 후보 매물 상세 조회
    public PropertyDetailResponse getProperty(Long requesterId, Long propertyId) {

        // 요청자의 후보 매물인지 확인
        CandidateProperty property = findOwnedProperty(propertyId, requesterId);
        return PropertyDetailResponse.from(property);
    }

    // 후보 매물 수정
    @Transactional
    public PropertyDetailResponse updateProperty(Long requesterId, Long propertyId, PropertyUpdateRequest request) {

        // 요청자의 후보 매물인지 확인
        CandidateProperty property = findOwnedProperty(propertyId, requesterId);

        property.updatePropertyDetails(request.name(), request.areaM2(), request.deposit(),
                request.monthlyRent(), request.keyMoney(), request.monthlyManagementFee());

        propertyRepository.flush();

        return PropertyDetailResponse.from(property);
    }


    // ----- helper -----
    private CandidateProperty findOwnedProperty(Long propertyId, Long requesterId) {
        return propertyRepository.findByIdAndStartupCondition_User_Id(propertyId, requesterId)
                .orElseThrow(() -> new BusinessException(PropertyErrorCode.PROPERTY_NOT_FOUND));
    }
}
