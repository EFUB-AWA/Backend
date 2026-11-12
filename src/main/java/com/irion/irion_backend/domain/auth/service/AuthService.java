package com.irion.irion_backend.domain.auth.service;

import com.irion.irion_backend.domain.auth.dto.LoginResult;
import com.irion.irion_backend.domain.auth.dto.LoginTokens;
import com.irion.irion_backend.domain.auth.dto.TokenPair;
import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.KakaoOAuthClient;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.dto.KakaoUserInfoResponse;
import com.irion.irion_backend.domain.auth.repository.RefreshTokenRepository;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.domain.user.service.UserService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.jwt.IssuedToken;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;


    // 로그인
    public LoginTokens kakaoLogin(String code) {
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(code);

        LoginResult result = userService.findByKakaoId(userInfo.id())
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> signUp(userInfo));

        return issueTokens(result);
    }

    // 회원가입
    private LoginResult signUp(KakaoUserInfoResponse userInfo) {
        if (userInfo.email() == null) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_EMAIL_REQUIRED);
        }
        if (!userInfo.hasVerifiedEmail()) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_EMAIL_NOT_VALID);
        }

        try {
            User user = userService.createKakaoUser(
                    userInfo.id(), userInfo.nickname(), userInfo.email(), userInfo.profileImageUrl());
            return new LoginResult(user, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 이미 가입한 경우
            User user = userService.findByKakaoId(userInfo.id())
                    .orElseThrow(() -> e);
            return new LoginResult(user, false);
        }
    }

    // 토큰 재발급
    public TokenPair reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
        }

        Claims claims = jwtProvider.parseRefreshToken(refreshToken);
        Long userId = jwtProvider.getUserId(claims);
        String jti = jwtProvider.getJti(claims);

        String savedJti = refreshTokenRepository.findJti(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND));


        // Redis에 저장된 RT가 jti와 다른 경우(이미 교체된 refresh가 다시 사용)
        if (!savedJti.equals(jti)) {
            refreshTokenRepository.delete(userId);
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
        }

        return issueAndSave(userId);
    }


    // ----- helper -----

    private LoginTokens issueTokens(LoginResult result) {
        Long userId = result.user().getId();
        TokenPair tokens = issueAndSave(userId);

        return LoginTokens.of(tokens.accessToken(), tokens.refreshToken(), result);
    }

    private TokenPair issueAndSave(Long userId) {
        IssuedToken accessToken = jwtProvider.createAccessToken(userId);
        IssuedToken refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenRepository.save(userId, refreshToken.jti(), refreshToken.expiresIn());

        return new TokenPair(accessToken, refreshToken);
    }
}
