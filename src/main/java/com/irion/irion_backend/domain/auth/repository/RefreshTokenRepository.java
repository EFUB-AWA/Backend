package com.irion.irion_backend.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String TOKEN_REFRESH_PREFIX = "RF:";

    private final StringRedisTemplate redisTemplate;

    private String createKey(Long userId) {
        return TOKEN_REFRESH_PREFIX + userId;
    }

    // refresh token의 jti를 redis에 추가
    public void save(Long userId, String jti, Duration ttl) {
        String key = createKey(userId);
        redisTemplate.opsForValue().set(key, jti, ttl);
    }

    public Optional<String> findJti(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(createKey(userId)));
    }

    public void delete(Long userId) {
        redisTemplate.delete(createKey(userId));
    }
}
