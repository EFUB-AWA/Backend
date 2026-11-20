package com.irion.irion_backend.domain.commercialStatistics.dto;

import java.math.BigDecimal;

/**
 * 서울시 추정매출-상권(OA-15572) 응답에서 사용하는 필드.
 *
 * 현재 반환하는 필드
 * - STDR_YYQU_CD      → quarterCode                    (기준 분기)
 * - TRDAR_CD          → commercialAreaCode             (상권 코드)
 * - SVC_INDUTY_CD     → industryCode                   (업종 코드)
 * - THSMON_SELNG_AMT  → reportedSalesAmountWon         (추정매출 금액, 원)
 * - THSMON_SELNG_CO   → reportedSalesTransactionCount  (매출 거래 건수)
 * - MDWK_SELNG_AMT    → reportedWeekdaySalesAmountWon  (주중 매출 금액, 원)
 * - WKEND_SELNG_AMT   → reportedWeekendSalesAmountWon  (주말 매출 금액, 원)
 *
 * < API에 있지만 현재 DTO에 담지 않는 필드>
 * - 상권 구분 코드·구분명·상권명·업종명
 *   TRDAR_SE_CD, TRDAR_SE_CD_NM, TRDAR_CD_NM, SVC_INDUTY_CD_NM
 * - 주중·주말 거래 건수
 *   MDWK_SELNG_CO, WKEND_SELNG_CO
 * - 요일별 매출 금액·거래 건수
 *   MON, TUES, WED, THUR, FRI, SAT, SUN + _SELNG_AMT 또는 _SELNG_CO
 * - 시간대별 매출 금액·거래 건수
 *   TMZON_00_06, TMZON_06_11, TMZON_11_14, TMZON_14_17,
 *   TMZON_17_21, TMZON_21_24 + _SELNG_AMT 또는 _SELNG_CO
 * - 성별 매출 금액·거래 건수
 *   ML, FML + _SELNG_AMT 또는 _SELNG_CO
 * - 연령대별 매출 금액·거래 건수
 *   AGRDE_10, AGRDE_20, AGRDE_30, AGRDE_40, AGRDE_50,
 *   AGRDE_60_ABOVE + _SELNG_AMT 또는 _SELNG_CO
 */

public record EstimatedSalesStatistics(
        String quarterCode,
        String commercialAreaCode,
        String industryCode,
        BigDecimal reportedSalesAmountWon,
        Long reportedSalesTransactionCount,
        BigDecimal reportedWeekdaySalesAmountWon,
        BigDecimal reportedWeekendSalesAmountWon
) {}