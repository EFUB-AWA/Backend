package com.irion.irion_backend.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties (
        @NotBlank String secret,
        @NotNull Duration accessExpiration,
        @NotNull Duration refreshExpiration
){}
