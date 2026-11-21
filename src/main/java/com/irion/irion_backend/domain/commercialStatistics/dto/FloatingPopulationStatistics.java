package com.irion.irion_backend.domain.commercialStatistics.dto;

/**
 * 서울시 길단위인구-상권(OA-15568) 조회 결과
 * 기준 분기, 상권 코드, 총 유동인구
 * 성별·연령대별·시간대별 유동인구
 */
public record FloatingPopulationStatistics(
        String quarterCode,
        String commercialAreaCode,
        Long totalFloatingPopulation,
        Long malePopulation,
        Long femalePopulation,
        Long age10sPopulation,
        Long age20sPopulation,
        Long age30sPopulation,
        Long age40sPopulation,
        Long age50sPopulation,
        Long age60AndOverPopulation,
        Long population00To06,
        Long population06To11,
        Long population11To14,
        Long population14To17,
        Long population17To21,
        Long population21To24
) {}
