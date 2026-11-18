package com.irion.irion_backend.domain.startupCondition.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record StartupConditionUpdateRequest(
        @NotBlank String title,
        @NotNull Long industryId,
        @NotBlank String preferredRegionCode,
        @NotNull @PositiveOrZero Long totalCapital,
        @NotNull @PositiveOrZero Long availableCapital,
        @NotNull @PositiveOrZero Long loanAmount,
        @NotNull @PositiveOrZero Long monthlyLaborCost,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal costOfSalesRate,
        @NotNull @PositiveOrZero Long monthlyOtherOperatingCost
) {
}