package com.irion.irion_backend.global.security.jwt;

import java.time.Duration;

public record IssuedToken(String token, String jti, Duration expiresIn) {
}
