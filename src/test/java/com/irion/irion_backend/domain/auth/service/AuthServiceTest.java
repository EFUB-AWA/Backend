package com.irion.irion_backend.domain.auth.service;

import com.irion.irion_backend.domain.auth.dto.LoginTokens;
import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.KakaoOAuthClient;
import com.irion.irion_backend.domain.auth.infrastructure.kakao.dto.KakaoUserInfoResponse;
import com.irion.irion_backend.domain.auth.repository.RefreshTokenRepository;
import com.irion.irion_backend.domain.user.entity.User;
import com.irion.irion_backend.domain.user.service.UserService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.jwt.JwtProperties;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    private static final String CODE = "test-authorization-code";
    private static final Long KAKAO_ID = 12345L;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-key-for-auth-service-test!!".getBytes(StandardCharsets.UTF_8)
    );

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // JwtProvider는 mock이 아닌 실제 객체 -> 발급된 토큰의 jti와 저장값 일치까지 검증
        JwtProvider jwtProvider = new JwtProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14))
        );
        authService = new AuthService(kakaoOAuthClient, userService,
                jwtProvider, refreshTokenRepository);
    }

    // 동시 가입 충돌 테스트
    @Test
    void 동시_가입으로_unique_충돌이_나면_재조회한_기존_회원으로_로그인() {
        // given: 처음 조회엔 없었지만, 가입 시도 중 다른 요청이 먼저 가입을 끝낸 상황
        User existingUser = createUser();

        given(kakaoOAuthClient.getUserInfo(CODE)).willReturn(kakaoUserInfo(true, true));
        given(userService.findByKakaoId(KAKAO_ID))
                .willReturn(Optional.empty())              // 1차 조회
                .willReturn(Optional.of(existingUser));    // 2차 조회
        given(userService.createKakaoUser(anyLong(), anyString(), anyString(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate kakao_id"));

        // when
        LoginTokens tokens = authService.kakaoLogin(CODE);

        // then: 신규 가입이 아닌 기존 회원으로 처리되고, 토큰 발급과 refresh 저장까지 정상 진행
        assertThat(tokens.isNewUser()).isFalse();
        assertThat(tokens.user()).isSameAs(existingUser);
        then(refreshTokenRepository).should()
                .save(USER_ID, tokens.refreshToken().jti(), tokens.refreshToken().expiresIn());
    }

    @Test
    void 충돌_후_재조회에도_회원이_없으면_원래_예외를_그대로_던진다() {
        // given: kakao_id가 아닌 다른 제약(예: email) 위반이라 재조회해도 없는 상황
        DataIntegrityViolationException exception = new DataIntegrityViolationException("other constraint");
        given(kakaoOAuthClient.getUserInfo(CODE)).willReturn(kakaoUserInfo(true, true));
        given(userService.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());
        given(userService.createKakaoUser(anyLong(), anyString(), anyString(), any()))
                .willThrow(exception);

        // when & then: 예외를 삼키지 않고 원래 예외 그대로 전파, 토큰은 발급되지 않음
        assertThatThrownBy(() -> authService.kakaoLogin(CODE))
                .isSameAs(exception);
        verifyNoInteractions(refreshTokenRepository);
    }


    // 이메일 검증

    @Test
    void 인증되지_않은_이메일이면_가입하지_않고_AUTH_KAKAO_EMAIL_NOT_VALID() {
        // given: 신규 회원인데 카카오 이메일이 미인증
        given(kakaoOAuthClient.getUserInfo(CODE)).willReturn(kakaoUserInfo(false, true));
        given(userService.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.kakaoLogin(CODE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.AUTH_KAKAO_EMAIL_NOT_VALID);

        // 부수 효과 없음: 가입 시도도, 토큰 저장도 일어나지 않아야 함
        then(userService).should(never()).createKakaoUser(anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void 유효하지_않은_이메일이면_가입하지_않고_AUTH_KAKAO_EMAIL_NOT_VALID() {
        // given: 인증은 됐지만 is_email_valid=false (만료되었거나 다른 계정에 재사용된 이메일)
        given(kakaoOAuthClient.getUserInfo(CODE)).willReturn(kakaoUserInfo(true, false));
        given(userService.findByKakaoId(KAKAO_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.kakaoLogin(CODE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.AUTH_KAKAO_EMAIL_NOT_VALID);

        then(userService).should(never()).createKakaoUser(anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(refreshTokenRepository);
    }


    // ----- helper ----- //

    private KakaoUserInfoResponse kakaoUserInfo(Boolean isEmailVerified, Boolean isEmailValid) {
        KakaoUserInfoResponse.Profile profile = new KakaoUserInfoResponse.Profile("테스트유저", null, null, true);
        KakaoUserInfoResponse.KakaoAccount account = new KakaoUserInfoResponse.KakaoAccount(profile, EMAIL, isEmailVerified, isEmailValid);
        return new KakaoUserInfoResponse(KAKAO_ID, "2026-11-12T00:00:00Z", account);
    }

    private User createUser() {
        User user = User.create("테스트유저", EMAIL, null, KAKAO_ID);
        // IDENTITY 전략이라 저장 전엔 id가 null → 테스트에서는 직접 주입
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
