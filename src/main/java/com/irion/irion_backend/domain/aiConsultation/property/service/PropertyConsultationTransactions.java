package com.irion.irion_backend.domain.aiConsultation.property.service;

import com.irion.irion_backend.domain.aiConsultation.property.client.ConsultationAiClient;
import com.irion.irion_backend.domain.aiConsultation.property.dto.response.PropertyConsultationDetailResponse;
import com.irion.irion_backend.domain.aiConsultation.property.entity.PropertyConsultation;
import com.irion.irion_backend.domain.aiConsultation.property.repository.PropertyConsultationRepository;
import com.irion.irion_backend.domain.analysis.dto.CalculatedPropertyResult;
import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.analysis.entity.PropertyAnalysis;
import com.irion.irion_backend.domain.analysis.repository.PropertyAnalysisRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PropertyConsultationTransactions {
    private final PropertyAnalysisRepository analyses;
    private final PropertyConsultationRepository consultations;

    record Reservation(PropertyConsultationDetailResponse detail, ConsultationAiClient.Input input) {}

    public Reservation reserve(Long requesterId, Long analysisId) {
        var analysis = analyses.findForConsultation(analysisId)
                .orElseThrow(() -> new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND));
        requireOwner(requesterId, analysis);
        var existing = consultations.findByPropertyAnalysis_Id(analysisId);
        if (existing.isPresent()) {
            return new Reservation(PropertyConsultationDetailResponse.from(existing.get()), null);
        }
        var input = inputFor(analysis);
        var pending = consultations.save(PropertyConsultation.pending(analysis));
        return new Reservation(PropertyConsultationDetailResponse.from(pending), input);
    }

    public Reservation reserveRetry(Long requesterId, Long consultationId, Long analysisId) {
        var consultation = consultations.findForUpdate(consultationId)
                .orElseThrow(() -> new BusinessException(CONSULTATION_NOT_FOUND));
        var analysis = consultation.getPropertyAnalysis();
        requireOwner(requesterId, analysis);
        if (!analysis.getId().equals(analysisId)) {
            throw new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND);
        }
        consultation.beginRetry();
        return new Reservation(PropertyConsultationDetailResponse.from(consultation), inputFor(analysis));
    }

    private void requireOwner(Long requesterId, PropertyAnalysis analysis) {
        if (!analysis.getAnalysisRun().getStartupCondition().getUser().getId().equals(requesterId)
                || !analysis.getCandidateProperty().getStartupCondition().getUser().getId().equals(requesterId)) {
            throw new BusinessException(CONSULTATION_ACCESS_DENIED);
        }
    }

    private ConsultationAiClient.Input inputFor(PropertyAnalysis analysis) {
        var run = analysis.getAnalysisRun();
        if (run.getStatus() != AnalysisStatus.COMPLETED || run.getConditionSnapshot().isEmpty()
                || analysis.getPropertySnapshot().isEmpty()) {
            throw new BusinessException(ANALYSIS_NOT_READY);
        }
        var metrics = new CalculatedPropertyResult(analysis.getCandidateProperty().getId(),
                analysis.getExpectedMonthlySales(), analysis.getMonthlyFixedCost(), analysis.getVariableCostRate(),
                analysis.getMonthlyOperatingProfit(), analysis.getBreakEvenSales(), analysis.getBreakEvenStatus(),
                analysis.getInitialRequiredCash(), analysis.getInitialOperatingCash(), analysis.getSafetyScore(),
                analysis.getSafetyGrade(), analysis.getRecommendationRank(), analysis.getRecommendationReason());
        try {
            metrics.requireComplete();
        } catch (NullPointerException missingMetric) {
            throw new BusinessException(ANALYSIS_NOT_READY);
        }
        var condition = new HashMap<>(run.getConditionSnapshot());
        condition.remove("userId");
        return new ConsultationAiClient.Input(condition, analysis.getPropertySnapshot(), metrics);
    }

    public PropertyConsultationDetailResponse complete(Long id, ConsultationAiClient.Result result) {
        var consultation = consultations.findForUpdate(id).orElseThrow(() -> new BusinessException(CONSULTATION_NOT_FOUND));
        consultation.complete(result.summary(), result.risks(), result.suggestions(), result.considerations(),
                result.model(), result.promptVersion());
        return PropertyConsultationDetailResponse.from(consultation);
    }

    public void fail(Long id) {
        consultations.findForUpdate(id).orElseThrow(() -> new BusinessException(CONSULTATION_NOT_FOUND)).fail();
    }
}
