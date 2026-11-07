package com.irion.irion_backend.global.security.jwt;

import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import com.irion.irion_backend.global.security.handler.JwtAuthenticationEntryPoint;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // 토큰이 존재하지 않는 경우
        if (ObjectUtils.isEmpty(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰이 존재하는 경우
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticate(token));
            SecurityContextHolder.setContext(context);
        } catch (BusinessException e) {
            request.setAttribute(JwtAuthenticationEntryPoint.AUTH_ERROR_CODE_ATTRIBUTE, e.getErrorCode());
        }

        filterChain.doFilter(request, response);
    }

    // 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);

        if (!ObjectUtils.isEmpty(token) && token.startsWith(TOKEN_PREFIX)) {
            return token.substring(TOKEN_PREFIX.length());
        }

        return null;
    }

    // 유효한 토큰인지 확인
    private Authentication authenticate(String token) {
        Claims claims = jwtProvider.parseAccessToken(token);
        String jti = jwtProvider.getJti(claims);

        if (tokenBlacklistRepository.isBlacklisted(jti)) {
            throw new BusinessException(AuthErrorCode.AUTH_LOGGED_OUT_TOKEN);
        }

        Long userId = jwtProvider.getUserId(claims);

        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
