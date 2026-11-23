package com.irion.irion_backend.domain.commercialStatistics.dto;

import java.math.BigDecimal;

/**
 * 서울시 소비-상권(OA-21278) 조회 결과
 * 기준 분기, 상권 코드, 총지출
 * 식료품·의류/신발·생활용품·의료·교통·여가·문화·교육·유흥 지출
 */
public record ConsumptionStatistics(
        String quarterCode,
        String commercialAreaCode,
        BigDecimal totalExpenditure,
        BigDecimal foodExpenditure,
        BigDecimal clothingAndFootwearExpenditure,
        BigDecimal householdGoodsExpenditure,
        BigDecimal medicalExpenditure,
        BigDecimal transportationExpenditure,
        BigDecimal leisureExpenditure,
        BigDecimal cultureExpenditure,
        BigDecimal educationExpenditure,
        BigDecimal entertainmentExpenditure
) {}
