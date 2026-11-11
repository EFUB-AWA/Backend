package com.irion.irion_backend.domain.auth.service;

import com.irion.irion_backend.domain.auth.dto.LoginResult;
import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.KakaoOAuthClient;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.dto.KakaoUserInfoResponse;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.domain.user.service.UserService;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;


    // 로그인
    public LoginResult kakaoLogin(String code) {
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(code);

        return userService.findByKakaoId(userInfo.id())
                .map(user -> new LoginResult(user, false))
                .orElseGet(() -> signUp(userInfo));
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
}
