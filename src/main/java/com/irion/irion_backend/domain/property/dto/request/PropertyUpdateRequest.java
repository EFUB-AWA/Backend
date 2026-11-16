package com.irion.irion_backend.domain.property.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PropertyUpdateRequest(
        @NotBlank(message = "매물 이름은 필수입니다.")
        @Size(max = 50, message = "매물 이름은 50자 이하여야 합니다.")
        String name,

        @NotNull(message = "면적은 필수입니다.")
        @Positive(message = "면적은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "면적은 소수점 2자리까지만 입력할 수 있습니다.")
        BigDecimal areaM2,

        @NotNull(message = "보증금은 필수입니다.")
        @PositiveOrZero(message = "보증금은 0 이상이어야 합니다.")
        Long deposit,

        @NotNull(message = "월세는 필수입니다.")
        @PositiveOrZero(message = "월세는 0 이상이어야 합니다.")
        Long monthlyRent,

        @NotNull(message = "권리금은 필수입니다.")
        @PositiveOrZero(message = "권리금은 0 이상이어야 합니다.")
        Long keyMoney,

        @NotNull(message = "관리비는 필수입니다.")
        @PositiveOrZero(message = "관리비는 0 이상이어야 합니다.")
        Long monthlyManagementFee
) {
}
