package com.irion.irion_backend.domain.auth.controller;

import com.irion.irion_backend.domain.auth.cookie.RefreshTokenCookieProvider;
import com.irion.irion_backend.domain.auth.dto.TokenPair;
import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.domain.auth.service.AuthService;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.jwt.IssuedToken;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    private static final Long USER_ID = 1L;
    private static final String REISSUE_URI = "/api/auth/reissue";
    private static final String LOGOUT_URI = "/api/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;   // 실제 빈: 필터를 통과할 진짜 access 토큰 발급용

    @MockitoBean
    private AuthService authService;   // 로직은 서비스 테스트에서 검증했으니 여기선 결과만 지정

    // ===== 토큰 재발급 =====

    @Test
    void 재발급_성공_시_새_refresh_쿠키와_access_토큰을_응답한다() throws Exception {
        // given
        IssuedToken access = jwtProvider.createAccessToken(USER_ID);
        IssuedToken refresh = jwtProvider.createRefreshToken(USER_ID);
        given(authService.reissue("old-refresh")).willReturn(new TokenPair(access, refresh));

        // when & then
        mockMvc.perform(post(REISSUE_URI)
                        .cookie(new Cookie(RefreshTokenCookieProvider.COOKIE_NAME, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(RefreshTokenCookieProvider.COOKIE_NAME + "=" + refresh.token()),
                        containsString("HttpOnly"),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=" + refresh.expiresIn().toSeconds()))))
                .andExpect(jsonPath("$.accessToken").value(access.token()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(access.expiresIn().toSeconds()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void 쿠키_없이_재발급하면_서비스에_null이_전달되고_401() throws Exception {
        // given
        given(authService.reissue(null))
                .willThrow(new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND));

        // when & then: required=false라 400이 아니라 서비스까지 도달해서 우리 에러 코드로 응답
        mockMvc.perform(post(REISSUE_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_NOT_FOUND"));

        then(authService).should().reissue(null);
    }

    // ===== 로그아웃 =====

    @Test
    void 로그아웃_성공_시_204와_refresh_쿠키_삭제를_응답한다() throws Exception {
        // given: 필터를 통과할 진짜 access 토큰
        IssuedToken access = jwtProvider.createAccessToken(USER_ID);

        // when & then
        mockMvc.perform(post(LOGOUT_URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access.token()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(RefreshTokenCookieProvider.COOKIE_NAME + "="),
                        containsString("Path=/api/auth"),
                        containsString("Max-Age=0"))));

        // @AuthMember로 주입된 userId와 Bearer를 뗀 토큰이 서비스에 전달됐는지
        then(authService).should().logout(USER_ID, access.token());
    }

    @Test
    void 토큰_없이_로그아웃하면_401이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post(LOGOUT_URI))
                .andExpect(status().isUnauthorized());

        then(authService).shouldHaveNoInteractions();
    }
}
