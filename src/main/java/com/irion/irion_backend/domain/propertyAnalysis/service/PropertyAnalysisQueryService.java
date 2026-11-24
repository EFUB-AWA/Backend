package com.irion.irion_backend.domain.propertyAnalysis.service;

import com.irion.irion_backend.domain.analysis.entity.AnalysisStatus;
import com.irion.irion_backend.domain.industry.entity.Industry;
import com.irion.irion_backend.domain.industry.repository.IndustryRepository;
import com.irion.irion_backend.domain.propertyAnalysis.dto.PropertyAnalysisDetailResponse;
import com.irion.irion_backend.domain.propertyAnalysis.repository.PropertyAnalysisQueryRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.ANALYSIS_NOT_COMPLETED;
import static com.irion.irion_backend.domain.propertyAnalysis.exception.PropertyAnalysisErrorCode.PROPERTY_ANALYSIS_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PropertyAnalysisQueryService {

    private final PropertyAnalysisQueryRepository propertyAnalyses;
    private final IndustryRepository industries;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PropertyAnalysisDetailResponse getDetail(Long requesterId, Long propertyAnalysisId) {
        var analysis = propertyAnalyses
                .findByIdAndAnalysisRun_StartupCondition_User_Id(propertyAnalysisId, requesterId)
                .orElseThrow(() -> new BusinessException(PROPERTY_ANALYSIS_NOT_FOUND));

        var run = analysis.getAnalysisRun();
        if (run.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(ANALYSIS_NOT_COMPLETED);
        }

        var condition = run.getConditionSnapshot();
        var property = analysis.getPropertySnapshot();
        var displayData = readDisplayData(
                run.getAssumptionsSnapshot(), analysis.getCandidateProperty().getId());

        Long industryId = snapshotLong(condition, "industryId");
        String industryName = industryId == null
                ? null
                : industries.findById(industryId).map(Industry::getName).orElse(null);

        var summary = new PropertyAnalysisDetailResponse.Summary(
                analysis.getSafetyScore(),
                mapSafetyGrade(analysis.getSafetyGrade()),
                analysis.getMonthlyOperatingProfit(),
                displayData.survivalMonths(),
                analysis.getBreakEvenSales()
        );

        Long monthlyLaborCost = snapshotLong(condition, "monthlyLaborCost");
        Long monthlyOtherOperatingCost = snapshotLong(condition, "monthlyOtherOperatingCost");
        Long monthlyTotalOperatingCost = monthlyLaborCost == null || monthlyOtherOperatingCost == null
                ? null
                : Math.addExact(monthlyLaborCost, monthlyOtherOperatingCost);

        var capitalBreakdown = new PropertyAnalysisDetailResponse.CapitalBreakdown(
                snapshotLong(condition, "totalCapital"),
                analysis.getInitialRequiredCash(),
                analysis.getInitialOperatingCash(),
                monthlyTotalOperatingCost,
                snapshotLong(property, "monthlyManagementFee"),
                analysis.getExpectedMonthlySales()
        );

        return new PropertyAnalysisDetailResponse(
                snapshotText(property, "name"),
                snapshotText(property, "address"),
                industryName,
                summary,
                capitalBreakdown,
                displayData.monthlyProfitForecast(),
                displayData.commercialTrend()
        );
    }

    private static String mapSafetyGrade(String storedGrade) {
        if (storedGrade == null) {
            return null;
        }

        return switch (storedGrade.strip().toUpperCase(Locale.ROOT)) {
            case "주의", "CAUTION" -> "주의";
            case "경고", "WARNING" -> "경고";
            case "안정", "STABLE" -> "안정";
            default -> null;
        };
    }

    private DisplayData readDisplayData(Map<String, Object> assumptions, Long candidatePropertyId) {
        if (assumptions != null
                && assumptions.get("propertyDisplayData") instanceof Map<?, ?> byProperty
                && byProperty.get(candidatePropertyId.toString()) instanceof Map<?, ?> stored) {
            return objectMapper.convertValue(stored, DisplayData.class);
        }
        return new DisplayData(null, List.of(), null);
    }

    private static Long snapshotLong(Map<String, Object> snapshot, String key) {
        return snapshot.get(key) instanceof Number number ? number.longValue() : null;
    }

    private static String snapshotText(Map<String, Object> snapshot, String key) {
        return snapshot.get(key) instanceof String text ? text : null;
    }

    public record DisplayData(
            BigDecimal survivalMonths,
            List<PropertyAnalysisDetailResponse.MonthlyProfitPoint> monthlyProfitForecast,
            PropertyAnalysisDetailResponse.CommercialTrend commercialTrend
    ) {
        public DisplayData {
            monthlyProfitForecast = monthlyProfitForecast == null
                    ? List.of()
                    : monthlyProfitForecast.stream()
                      .sorted(Comparator.comparing(PropertyAnalysisDetailResponse.MonthlyProfitPoint::month))
                      .toList();
        }
    }
}