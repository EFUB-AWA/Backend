package com.irion.irion_backend.global.security.jwt;

import com.irion.irion_backend.domain.auth.exception.AuthErrorCode;
import com.irion.irion_backend.global.error.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    // 토큰 발급
    public IssuedToken createAccessToken(Long userId) {
        return issue(userId, ACCESS_TYPE, jwtProperties.accessExpiration());
    }

    public IssuedToken createRefreshToken(Long userId) {
        return issue(userId, REFRESH_TYPE, jwtProperties.refreshExpiration());
    }

    private IssuedToken issue(Long userId, String type, Duration validity) {
        String jti = UUID.randomUUID().toString();
        String token = createToken(userId, jti, type, validity);
        return new IssuedToken(token, jti, validity);
    }

    private String createToken(Long userId, String jti, String type, Duration validity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)    // jti: 블랙리스트 키
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(secretKey)
                .compact();
    }


    // 검증
    public Claims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!ACCESS_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_TOKEN);
        }
    }


    // claims에서 값 추출
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    // 남은 유효 시간, 로그아웃 시 블랙리스트 TTL로 사용
    public Duration getRemainingValidity(Claims claims) {
        Duration remaining = Duration.between(
                Instant.now(), claims.getExpiration().toInstant());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
