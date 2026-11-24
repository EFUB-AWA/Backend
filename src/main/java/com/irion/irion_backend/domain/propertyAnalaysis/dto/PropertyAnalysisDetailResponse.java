package com.irion.irion_backend.domain.propertyAnalaysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record PropertyAnalysisDetailResponse(
        String propertyName,
        String address,
        String industryName,
        Summary summary,
        CapitalBreakdown capitalBreakdown,
        List<MonthlyProfitPoint> monthlyProfitForecast,
        CommercialTrend commercialTrend
) {
    public record Summary( //상단 카드 4개
            BigDecimal safetyScore,
            String safetyGrade,
            Long monthlyOperatingProfit,
            BigDecimal survivalMonths,  //생존 가능 기간
            Long breakEvenSales         //손익분기점
    ) {}

    public record CapitalBreakdown(         //자금 구성
            Long totalCapital,                 //보유 자금
            Long initialRequiredCash,       //초기 비용
            Long initialOperatingCash,      //잔여 운영자금
            Long monthlyTotalOperatingCost,
            Long monthlyManagementFee,
            Long expectedMonthlySales
    ) {}

    public record MonthlyProfitPoint(   //예상 월 수익
            String month,
            Long operatingProfitWon
    ) {}

    //저장된 상권 통계에서 화면에 필요한 points만 읽고 추가 필드는 무시
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommercialTrend(List<CommercialTrendPoint> points) {
        public CommercialTrend {
            points = points == null
                    ? List.of()
                    : points.stream()
                      .sorted(Comparator.comparing(CommercialTrendPoint::quarterCode))
                      .toList();
        }
    }

    public record CommercialTrendPoint(     //상권 분석
            String quarterCode,
            BigDecimal reportedSalesAmountWon,
            BigDecimal openingRatePercent,
            BigDecimal closureRatePercent
    ) {}
}