package com.irion.irion_backend.domain.aiConsultation.comparison.service;

import com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationListResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.repository.ComparisonConsultationRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComparisonConsultationService {

    private final ComparisonConsultationRepository comparisonConsultationRepository;

    public ComparisonConsultationDetailResponse getComparisonConsultation(Long requesterId, Long comparisonConsultationId) {

        ComparisonConsultation consultation = findOwned(comparisonConsultationId, requesterId);

        if (consultation.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_EXPIRED);
        }

        return ComparisonConsultationDetailResponse.from(consultation);
    }

    public ComparisonConsultationListResponse getComparisonConsultations(Long requesterId, Long cursor,
                                                                         int size) {

        Pageable pageable = PageRequest.of(0, size + 1);
        List<ComparisonConsultation> consultationList = findOwnedConsultationList(requesterId, cursor, pageable);

        return ComparisonConsultationListResponse.of(consultationList, size);
    }


    // ----- helper -----

    private ComparisonConsultation findOwned(Long comparisonConsultationId, Long requesterId) {
        return comparisonConsultationRepository.findOwnedById(comparisonConsultationId, requesterId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));
    }

    private List<ComparisonConsultation> findOwnedConsultationList(Long requesterId, Long cursor,
                                                                   Pageable pageable) {
        if (cursor == null) {
            return comparisonConsultationRepository.findSavedFirstPage(requesterId, pageable);
        }
        return comparisonConsultationRepository.findSavedNextPage(requesterId, cursor, pageable);
    }
}
