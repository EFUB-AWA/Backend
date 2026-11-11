package com.irion.irion_backend.domain.auth.controller;

import com.irion.irion_backend.domain.auth.cookie.RefreshTokenCookieProvider;
import com.irion.irion_backend.domain.auth.dto.AuthResponse;
import com.irion.irion_backend.domain.auth.dto.KakaoLoginRequest;
import com.irion.irion_backend.domain.auth.dto.LoginTokens;
import com.irion.irion_backend.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping("/kakao/login")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        LoginTokens tokens = authService.kakaoLogin(request.code());
        ResponseCookie cookie = refreshTokenCookieProvider.create(tokens.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.from(tokens));
    }
}
