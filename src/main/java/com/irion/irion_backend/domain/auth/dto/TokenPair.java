package com.irion.irion_backend.domain.auth.dto;

import com.irion.irion_backend.global.security.jwt.IssuedToken;

public record TokenPair(
        IssuedToken accessToken,
        IssuedToken refreshToken
) {
}
