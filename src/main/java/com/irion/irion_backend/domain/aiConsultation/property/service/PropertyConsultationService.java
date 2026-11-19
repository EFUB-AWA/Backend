package com.irion.irion_backend.domain.aiConsultation.property.service;

import com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode;
import com.irion.irion_backend.domain.aiConsultation.property.dto.response.PropertyConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.property.repository.PropertyConsultationRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyConsultationService {

    private final PropertyConsultationRepository propertyConsultationRepository;

    // 후보 매물 AI 컨설팅 조회
    public PropertyConsultationDetailResponse getPropertyConsultation(Long requesterId, Long propertyConsultationId) {

        PropertyConsultation propertyConsultation = findOwnedPropertyConsultation(propertyConsultationId, requesterId);
        return PropertyConsultationDetailResponse.from(propertyConsultation);
    }


    // ----- helper -----

    // 요청자 소유의 propertyConsultation인지 확인
    private PropertyConsultation findOwnedPropertyConsultation(Long propertyConsultationId, Long requesterId) {

        PropertyConsultation propertyConsultation = propertyConsultationRepository.findById(propertyConsultationId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));

        Long ownerId = propertyConsultation.getPropertyAnalysis().getCandidateProperty().getStartupCondition().getUser().getId();
        if (!ownerId.equals(requesterId)) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND);
        }

        return propertyConsultation;
    }
}
