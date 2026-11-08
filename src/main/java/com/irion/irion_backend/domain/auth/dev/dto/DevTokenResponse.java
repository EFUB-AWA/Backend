package com.irion.irion_backend.domain.auth.dev.dto;

public record DevTokenResponse(
        String accessToken
) {

    public static DevTokenResponse of(String accessToken) {
        return new DevTokenResponse(accessToken);
    }
}
