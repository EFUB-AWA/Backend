package com.irion.irion_backend.domain.auth.controller;

import com.irion.irion_backend.domain.auth.cookie.RefreshTokenCookieProvider;
import com.irion.irion_backend.domain.auth.dto.TokenPair;
import com.irion.irion_backend.domain.auth.dto.response.AuthResponse;
import com.irion.irion_backend.domain.auth.dto.request.KakaoLoginRequest;
import com.irion.irion_backend.domain.auth.dto.LoginTokens;
import com.irion.irion_backend.domain.auth.dto.response.ReissueResponse;
import com.irion.irion_backend.domain.auth.service.AuthService;
import com.irion.irion_backend.global.security.auth.AuthMember;
import com.irion.irion_backend.global.security.jwt.BearerTokenResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/kakao/login")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        LoginTokens tokens = authService.kakaoLogin(request.authorizationCode());
        ResponseCookie cookie = refreshTokenCookieProvider.create(tokens.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.from(tokens));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken) {
        TokenPair tokens = authService.reissue(refreshToken);
        ResponseCookie cookie = refreshTokenCookieProvider.create(tokens.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(ReissueResponse.from(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthMember Long requesterId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(requesterId, BearerTokenResolver.resolve(authorization));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString())
                .build();
    }
}
