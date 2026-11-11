package com.irion.irion_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KakaoLoginRequest(
        @NotBlank
        @Size(max = 512)
        String code
) {}
