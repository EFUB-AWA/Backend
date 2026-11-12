package com.irion.irion_backend.domain.auth.dto;

public record ReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    private static final String BEARER = "Bearer";

    public static ReissueResponse from(ReissueTokens tokens) {
        return new ReissueResponse(
                tokens.accessToken().token(),
                BEARER,
                tokens.accessToken().expiresIn().toSeconds()
        );
    }
}
