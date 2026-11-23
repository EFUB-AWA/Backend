package com.irion.irion_backend.domain.aiConsultation.property.service;

import com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode;
import com.irion.irion_backend.domain.aiConsultation.property.client.ConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.property.dto.response.PropertyConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.property.exception.PropertyConsultationGenerationException;
import com.irion.irion_backend.domain.aiConsultation.property.repository.PropertyConsultationRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyConsultationService {

    private final PropertyConsultationRepository propertyConsultationRepository;
    private final PropertyConsultationTransactions transactions;
    private final ConsultationAiClient ai;

    public record Creation(boolean created, PropertyConsultationDetailResponse detail) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Creation create(Long requesterId, Long analysisId) {
        var reservation = transactions.reserve(requesterId, analysisId);
        if (reservation.input() == null) {
            return new Creation(false, reservation.detail());
        }
        return new Creation(true, generate(reservation));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PropertyConsultationDetailResponse retry(Long requesterId, Long consultationId, Long analysisId) {
        return generate(transactions.reserveRetry(requesterId, consultationId, analysisId));
    }

    private PropertyConsultationDetailResponse generate(PropertyConsultationTransactions.Reservation reservation) {
        Long id = reservation.detail().propertyConsultId();
        try {
            var result = ai.generate(reservation.input());
            return transactions.complete(id, result);
        } catch (RuntimeException cause) {
            transactions.fail(id);
            if (cause instanceof BusinessException business) {
                throw new PropertyConsultationGenerationException(id, business);
            }
            throw cause;
        }
    }

    // 후보 매물 AI 컨설팅 조회
    public PropertyConsultationDetailResponse getPropertyConsultation(Long requesterId, Long propertyConsultationId) {

        PropertyConsultation propertyConsultation = findOwnedPropertyConsultation(propertyConsultationId, requesterId);
        return PropertyConsultationDetailResponse.from(propertyConsultation);
    }


    // ----- helper -----

    // 요청자 소유의 propertyConsultation인지 확인
    private PropertyConsultation findOwnedPropertyConsultation(Long propertyConsultationId, Long requesterId) {

        return propertyConsultationRepository.findOwnedById(propertyConsultationId, requesterId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));
    }
}
