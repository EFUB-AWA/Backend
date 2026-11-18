package com.irion.irion_backend.domain.startupCondition.dto.response;

import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StartupConditionDetailResponse(
        Long startupConditionId,
        String title,
        Long industryId,
        String preferredRegionCode,
        Long totalCapital,
        Long availableCapital,
        Long loanAmount,
        Long monthlyLaborCost,
        BigDecimal costOfSalesRate,
        Long monthlyOtherOperatingCost,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StartupConditionDetailResponse from(StartupCondition condition) {
        return new StartupConditionDetailResponse(
                condition.getId(),
                condition.getTitle(),
                condition.getIndustry().getId(),
                condition.getPreferredRegionCode(),
                condition.getTotalCapital(),
                condition.getAvailableCapital(),
                condition.getLoanAmount(),
                condition.getMonthlyLaborCost(),
                condition.getCostOfSalesRate(),
                condition.getMonthlyOtherOperatingCost(),
                condition.getCreatedAt(),
                condition.getUpdatedAt()
        );
    }
}