package com.irion.irion_backend.global.security;

import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.global.security.jwt.JwtProperties;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import com.irion.irion_backend.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JwtAuthenticationIntegrationTest {

    private static final String TEST_URI = "/api/test/me";
    private static final String FORGED_SECRET = Base64.getEncoder().encodeToString(
            "another-secret-key-for-forged-token-test!!!".getBytes(StandardCharsets.UTF_8));


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TokenBlacklistRepository tokenBlacklistRepository;


    @Test
    void 유효한_토큰_입력_시_200_body_1() throws Exception {
        // given: 유효한 토큰
        String token = jwtProvider.createAccessToken(1L);

        // when & then: 1 반환
        mockMvc.perform(get(TEST_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }


    @Test
    void 토큰_없음_401_AUTH_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get(TEST_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.path").value(TEST_URI));
    }


    @Test
    void 만료된_토큰이면_401_AUTH_TOKEN_EXPIRED() throws Exception{
        // given: 서버와 같은 secret, 만료시간 -1초
        JwtProvider expiredProvider = createProvider(jwtProperties.secret(), Duration.ofSeconds(-1));
        String expiredToken = expiredProvider.createAccessToken(1L);

        // when & then
        assertUnauthorized(expiredToken, AuthErrorCode.AUTH_TOKEN_EXPIRED);
    }


    @Test
    void 위조된_토큰이면_401_AUTH_INVALID_TOKEN() throws Exception {
        // given: 다른 secret으로 만든 위조 토큰
        JwtProvider forgedProvider = createProvider(
                FORGED_SECRET,
                jwtProperties.accessExpiration()
        );

        String forgedToken = forgedProvider.createAccessToken(1L);

        // when & then
        assertUnauthorized(forgedToken, AuthErrorCode.AUTH_INVALID_TOKEN);
    }


    @Test
    void 블랙리스트에_등록된_토큰이면_401_AUTH_LOGGED_OUT_TOKEN() throws Exception {
        // given: 블랙리스트에 TTL 1분으로 등록
        String blacklistedToken = jwtProvider.createAccessToken(1L);
        String jti = jwtProvider.getJti(jwtProvider.parseAccessToken(blacklistedToken));
        tokenBlacklistRepository.add(jti, Duration.ofMinutes(1));

        // when & then
        assertUnauthorized(blacklistedToken, AuthErrorCode.AUTH_LOGGED_OUT_TOKEN);
    }


    @Test
    void refresh_토큰이면_401_AUTH_INVALID_TOKEN() throws Exception {
        // given: refresh token
        String refreshToken = jwtProvider.createRefreshToken(1L);

        // when & then
        assertUnauthorized(refreshToken, AuthErrorCode.AUTH_INVALID_TOKEN);
    }


    // ----- helper ----- //
    private JwtProvider createProvider(String secret, Duration duration) {
        return new JwtProvider(new JwtProperties(secret, duration, Duration.ofMinutes(30)));
    }

    private void assertUnauthorized(String token, AuthErrorCode expected) throws Exception {
        mockMvc.perform(get(TEST_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(expected.getCode()))
                .andExpect(jsonPath("$.path").value(TEST_URI));
    }
}
