package com.irion.irion_backend.domain.auth.dto.response;

import com.irion.irion_backend.domain.auth.dto.TokenPair;

public record ReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    private static final String BEARER = "Bearer";

    public static ReissueResponse from(TokenPair tokens) {
        return new ReissueResponse(
                tokens.accessToken().token(),
                BEARER,
                tokens.accessToken().expiresIn().toSeconds()
        );
    }
}
