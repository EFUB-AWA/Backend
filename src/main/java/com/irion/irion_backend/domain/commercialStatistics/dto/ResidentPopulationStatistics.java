package com.irion.irion_backend.domain.commercialStatistics.dto;

/**
 * 서울시 상주인구-상권(OA-15584) 조회 결과
 * 기준 분기, 상권 코드, 총·성별·연령대별 상주인구
 * 전체·아파트·비아파트 가구 수
 */
public record ResidentPopulationStatistics(
        String quarterCode,
        String commercialAreaCode,
        Long totalResidentPopulation,
        Long malePopulation,
        Long femalePopulation,
        Long age10sPopulation,
        Long age20sPopulation,
        Long age30sPopulation,
        Long age40sPopulation,
        Long age50sPopulation,
        Long age60AndOverPopulation,
        Long totalHouseholdCount,
        Long apartmentHouseholdCount,
        Long nonApartmentHouseholdCount
) {}
