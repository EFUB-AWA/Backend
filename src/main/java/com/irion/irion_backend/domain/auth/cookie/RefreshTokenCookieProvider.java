package com.irion.irion_backend.domain.auth.cookie;

import com.irion.irion_backend.global.config.properties.CookieProperties;
import com.irion.irion_backend.global.security.jwt.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final CookieProperties cookieProperties;

    public ResponseCookie create(IssuedToken refreshToken) {
        return baseCookie(refreshToken.token())
                .maxAge(refreshToken.expiresIn())
                .build();
    }

    public ResponseCookie delete() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(COOKIE_PATH);
    }
}
