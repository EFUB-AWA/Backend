package com.irion.irion_backend.global.security.jwt;

import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static com.irion.irion_backend.domain.auth.exception.AuthErrorCode.AUTH_INVALID_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtProviderTest {

    private static final String SECRET = base64("test-secret-key-for-jwt-unit-testing-only");
    private static final String OTHER_SECRET = base64("test-other-secret-key-for-jwt-testing-only");

    private final JwtProvider jwtProvider = createProvider(SECRET, Duration.ofMinutes(30));

    @Test
    void 발급한_토큰에서_userId를_추출한다() {
        String token = jwtProvider.createAccessToken(1L).token();
        Claims claims = jwtProvider.parseAccessToken(token);

        assertThat(jwtProvider.getUserId(claims)).isEqualTo(1L);
    }

    @Test
    void 같은_userId로_발급해도_jti는_다르다() {
        Claims first = jwtProvider.parseAccessToken(jwtProvider.createAccessToken(1L).token());
        Claims second = jwtProvider.parseAccessToken(jwtProvider.createAccessToken(1L).token());

        assertThat(jwtProvider.getJti(first)).isNotEqualTo(jwtProvider.getJti(second));
    }

    @Test
    void 만료된_토큰은_AUTH_TOKEN_EXPIRED() {
        JwtProvider expiredProvider = createProvider(SECRET, Duration.ofSeconds(-1));
        String token = expiredProvider.createAccessToken(1L).token();

        assertErrorCode(token, AuthErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void 다른_키로_서명된_토큰은_AUTH_INVALID_TOKEN() {
        JwtProvider otherProvider = createProvider(OTHER_SECRET, Duration.ofMinutes(30));
        String forgedToken = otherProvider.createAccessToken(1L).token();

        assertErrorCode(forgedToken, AUTH_INVALID_TOKEN);
    }

    @Test
    void 형식이_잘못된_토큰은_AUTH_INVALID_TOKEN() {
        assertErrorCode("abc", AUTH_INVALID_TOKEN);
    }

    @Test
    void 빈_토큰은_AUTH_INVALID_TOKEN() {
        assertErrorCode("", AUTH_INVALID_TOKEN);
    }

    @Test
    void refresh_token을_access_token으로_사용하면_AUTH_INVALID_TOKEN() {
        String refreshToken = jwtProvider.createRefreshToken(1L).token();

        assertErrorCode(refreshToken, AUTH_INVALID_TOKEN);
    }

    @Test
    void 남은_유효시간은_0보다_크고_발급_유효시간_이하다() {
        Claims claims = jwtProvider.parseAccessToken(jwtProvider.createAccessToken(1L).token());

        Duration remaining = jwtProvider.getRemainingValidity(claims);

        assertThat(remaining).isPositive();
        assertThat(remaining).isLessThanOrEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void 발급_결과의_jti와_만료시간이_토큰과_일치한다() {
        IssuedToken issued = jwtProvider.createAccessToken(1L);
        Claims claims = jwtProvider.parseAccessToken(issued.token());

        assertThat(jwtProvider.getJti(claims)).isEqualTo(issued.jti());
        assertThat(issued.expiresIn()).isEqualTo(Duration.ofMinutes(30));
    }



    // ---- helpers ---- //

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JwtProvider createProvider(String secret, Duration accessExpiration) {
        return new JwtProvider(new JwtProperties(secret, accessExpiration, Duration.ofDays(14)));
    }

    private void assertErrorCode(String token, AuthErrorCode expected) {
        assertThatThrownBy(() -> jwtProvider.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
