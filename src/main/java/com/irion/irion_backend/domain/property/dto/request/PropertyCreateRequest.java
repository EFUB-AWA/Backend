package com.irion.irion_backend.domain.property.dto.request;

import com.irion.irion_backend.domain.property.entity.CandidateProperty;
import com.irion.irion_backend.domain.startupCondition.entity.StartupCondition;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PropertyCreateRequest(
        @NotNull(message = "창업 조건 ID는 필수입니다.")
        Long startupConditionId,

        @NotBlank(message = "매물 이름은 필수입니다.")
        @Size(max = 50, message = "매물 이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
        String addressDetail,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "33.0", message = "위도가 국내 범위를 벗어났습니다.")
        @DecimalMax(value = "39.0", message = "위도가 국내 범위를 벗어났습니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "124.0", message = "경도가 국내 범위를 벗어났습니다.")
        @DecimalMax(value = "132.0", message = "경도가 국내 범위를 벗어났습니다.")
        Double longitude,

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
        Long monthlyManagementFee,

        @NotNull(message = "기타 초기 비용은 필수입니다.")
        @PositiveOrZero(message = "기타 초기 비용은 0 이상이어야 합니다.")
        Long otherInitialCost
) {

    public CandidateProperty toEntity(StartupCondition startupCondition) {
        return CandidateProperty.builder()
                .startupCondition(startupCondition)
                .name(name)
                .address(address)
                .addressDetail(addressDetail)
                .latitude(latitude)
                .longitude(longitude)
                .areaM2(areaM2)
                .deposit(deposit)
                .monthlyRent(monthlyRent)
                .keyMoney(keyMoney)
                .monthlyManagementFee(monthlyManagementFee)
                .otherInitialCost(otherInitialCost)
                .build();
    }
}
