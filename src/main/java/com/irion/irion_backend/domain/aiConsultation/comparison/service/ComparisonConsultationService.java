package com.irion.irion_backend.domain.aiConsultation.comparison.service;

import com.irion.irion_backend.domain.aiConsultation.common.enums.ConsultationStatus;
import com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationListResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.dto.response.ComparisonConsultationSaveResponse;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation;
import com.irion.irion_backend.domain.aiConsultation.comparison.repository.ComparisonConsultationRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.hibernate.exception.ConstraintViolationException;
import com.irion.irion_backend.domain.aiConsultation.comparison.client.ComparisonConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.comparison.entity.ComparisonConsultation.Report;
import com.irion.irion_backend.domain.aiConsultation.comparison.exception.ComparisonGenerationException;

@lombok.extern.slf4j.Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComparisonConsultationService {

    private final ComparisonConsultationRepository comparisonConsultationRepository;

    private final ComparisonConsultationTransactions transactions;
    private final ComparisonConsultationAiClient ai;

    public record Creation(boolean created, ComparisonConsultationDetailResponse detail) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Creation create(Long requesterId, List<Long> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 3 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size())
            throw new BusinessException(ConsultationErrorCode.INVALID_COMPARISON_SELECTION);
        String key = ids.stream().sorted().map(Objects::toString).collect(Collectors.joining(":"));
        ComparisonConsultationTransactions.Reservation reservation;
        try {
            reservation = transactions.reserve(requesterId, ids, key);
        } catch (DataIntegrityViolationException failure) {
            if (!isComparisonKeyConflict(failure)) throw failure;
            reservation = transactions.reusable(requesterId, key).orElseThrow(() -> failure);
        }
        if (reservation.input() == null) return new Creation(false, reservation.detail());
        long id = reservation.detail().comparisonConsultId();
        Report report;
        try {
            report = ai.generate(ComparisonConsultationAiClient.Input.from(reservation.input()));
        } catch (BusinessException failure) {
            transactions.fail(id);
            throw new ComparisonGenerationException(id, failure);
        } catch (RuntimeException failure) {
            log.error("Unexpected comparison generation error id={} type={} location={}", id,
                    failure.getClass().getName(), failure.getStackTrace().length == 0 ? "unknown" : failure.getStackTrace()[0]);
            transactions.fail(id);
            throw new ComparisonGenerationException(id, new BusinessException(
                    com.irion.irion_backend.global.error.GlobalErrorCode.INTERNAL_SERVER_ERROR));
        }
        return new Creation(true, transactions.complete(id, report));
    }

    private boolean isComparisonKeyConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && "23505".equals(violation.getSQLState())
                    && "uq_comparison_user_key".equals(violation.getConstraintName())) return true;
        }
        return false;
    }

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

    @Transactional
    public ComparisonConsultationSaveResponse saveComparisonConsultation(Long requesterId, Long comparisonConsultationId) {

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        ComparisonConsultation consultation = findOwned(comparisonConsultationId, requesterId);

        // 이미 저장된 컨설팅은 그대로 반환
        if (consultation.isSaved()) {
            return ComparisonConsultationSaveResponse.from(consultation);
        }

        checkIsCompleted(consultation);

        if (consultation.isExpired(now)) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_EXPIRED);
        }

        int updated = comparisonConsultationRepository.updateSaved(consultation.getId(), now, ConsultationStatus.COMPLETED);

        // 저장됐으면 재조회 없이 바로 응답
        if (updated == 1) {
            return ComparisonConsultationSaveResponse.of(consultation.getId(), now);
        }

        ComparisonConsultation current = comparisonConsultationRepository.findById(consultation.getId())
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_EXPIRED));

        if (current.isSaved()) {
            return ComparisonConsultationSaveResponse.from(current);
        }
        throw new BusinessException(ConsultationErrorCode.CONSULTATION_EXPIRED);
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

    private void checkIsCompleted(ComparisonConsultation consultation) {
        if (consultation.getStatus() == ConsultationStatus.PENDING) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_COMPLETED);
        }
        if (consultation.getStatus() == ConsultationStatus.FAILED) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_FAILED);
        }
    }
}
