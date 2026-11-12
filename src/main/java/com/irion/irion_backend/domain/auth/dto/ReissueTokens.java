package com.irion.irion_backend.domain.auth.dto;

import com.irion.irion_backend.global.security.jwt.IssuedToken;

public record ReissueTokens(
        IssuedToken accessToken,
        IssuedToken refreshToken
) {
}
