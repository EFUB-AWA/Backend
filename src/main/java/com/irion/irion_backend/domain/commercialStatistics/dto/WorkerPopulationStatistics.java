package com.irion.irion_backend.domain.commercialStatistics.dto;

/**
 * 서울시 직장인구-상권(OA-15569) 조회 결과
 * 기준 분기, 상권 코드, 총·성별·연령대별 직장인구
 */
public record WorkerPopulationStatistics(
        String quarterCode,
        String commercialAreaCode,
        Long totalWorkerPopulation,
        Long malePopulation,
        Long femalePopulation,
        Long age10sPopulation,
        Long age20sPopulation,
        Long age30sPopulation,
        Long age40sPopulation,
        Long age50sPopulation,
        Long age60AndOverPopulation
) {}
