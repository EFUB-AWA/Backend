package com.irion.irion_backend.domain.commercialStatistics.dto;

import java.math.BigDecimal;

/**
 * 서울시 점포-상권(OA-15577) 조회 결과
 * 기준 분기, 상권 코드, 업종 코드
 * 전체·일반·프랜차이즈 점포 수, 개업·폐업 점포 수, 개업률·폐업률
 */

public record StoreStatistics(
        String quarterCode,
        String commercialAreaCode,
        String industryCode,
        Long totalStoreCount,
        Long generalStoreCount,
        Long franchiseStoreCount,
        Long openedStoreCount,
        Long closedStoreCount,
        BigDecimal openingRate,
        BigDecimal closureRate
) {}
