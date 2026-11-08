package com.irion.irion_backend.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private static final String TOKEN_BLACKLIST_PREFIX = "BL:";
    private static final String BLACKLIST_VALUE = "logout";

    private final StringRedisTemplate redisTemplate;

    private static String createKey(String jti) {
        return TOKEN_BLACKLIST_PREFIX + jti;
    }

    // 블랙 리스트에 존재하는지 확인
    public boolean isBlacklisted(String jti) {
        String key = createKey(jti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 블랙 리스트에 추가
    public void add(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String key = createKey(jti);
        redisTemplate.opsForValue().set(key, BLACKLIST_VALUE, ttl);
    }
}
