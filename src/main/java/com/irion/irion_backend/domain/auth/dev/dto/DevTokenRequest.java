package com.irion.irion_backend.domain.auth.dev.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DevTokenRequest(
        @NotNull
        @Positive
        Long userId
) {}
