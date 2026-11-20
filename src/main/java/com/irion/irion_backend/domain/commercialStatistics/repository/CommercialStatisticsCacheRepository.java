package com.irion.irion_backend.domain.commercialStatistics.repository;

import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.COMMERCIAL_STATS_CACHE_FAILED;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CommercialStatisticsCacheRepository {

    private static final String SALES_KEY_PREFIX = "commercial-stats:v1:sales:";
    private static final String LATEST_SALES_QUARTER_KEY = SALES_KEY_PREFIX + "latest-ready-quarter";
    private static final String READY_FIELD = "__ready__";
    private static final int WRITE_BATCH_SIZE = 1000;

    private static final Duration SALES_TTL = Duration.ofDays(14);
    private static final Duration EMPTY_SALES_TTL = Duration.ofMinutes(15);
    private static final Duration TEMPORARY_KEY_TTL = Duration.ofHours(1);

    private static final RedisScript<Long> START_WRITE = RedisScript.of("""
            redis.call('HSET', KEYS[1], ARGV[1], '1')
            return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            """, Long.class);

    private static final RedisScript<Long> PUBLISH = RedisScript.of("""
            if redis.call('HGET', KEYS[1], ARGV[1]) ~= '1'
                    or redis.call('HLEN', KEYS[1]) ~= tonumber(ARGV[2]) then
                return 0
            end
            if tonumber(ARGV[2]) == 1 then
                redis.call('HSET', KEYS[1], ARGV[1], 'empty')
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            redis.call('RENAME', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final RedisScript<Long> PUBLISH_LATEST_QUARTER = RedisScript.of("""
            local current = redis.call('GET', KEYS[1])
            if not current or ARGV[1] > current then
                redis.call('SET', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """, Long.class);

    private static final RedisScript<Long> PUBLISHED_QUARTER_TTL = RedisScript.of("""
            if redis.call('HGET', KEYS[1], ARGV[1]) ~= '1' or redis.call('HLEN', KEYS[1]) < 2 then
                return -2
            end
            return redis.call('PTTL', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SalesLookup findSales(String quarterCode, String commercialAreaCode, String industryCode) {
        return findSales(quarterCode, commercialAreaCode, industryCode, true);
    }

    public SalesLookup findPublishedSales(String quarterCode, String commercialAreaCode, String industryCode) {
        return findSales(quarterCode, commercialAreaCode, industryCode, false);
    }

    private SalesLookup findSales(
            String quarterCode, String commercialAreaCode, String industryCode, boolean acceptEmptyQuarter
    ) {
        String field = salesField(commercialAreaCode, industryCode);

        try {
            HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
            List<String> values = hashOperations.multiGet(salesKey(quarterCode), List.of(READY_FIELD, field));

            String readyState = values.get(0);
            if (!"1".equals(readyState) && !(acceptEmptyQuarter && "empty".equals(readyState))) {
                return new SalesLookup(false, Optional.empty());
            }

            String json = values.get(1);
            if (json == null) {
                return new SalesLookup(true, Optional.empty());
            }

            EstimatedSalesStatistics statistics = objectMapper.readValue(json, EstimatedSalesStatistics.class);
            if (statistics == null) {
                throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
            }

            return new SalesLookup(true, Optional.of(statistics));
        } catch (DataAccessException | JacksonException e) {
            log.warn("추정매출 캐시 조회 실패. quarter={}, type={}", quarterCode, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public Optional<String> findLatestSalesQuarter() {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(LATEST_SALES_QUARTER_KEY));
        } catch (DataAccessException e) {
            log.warn("추정매출 최신 분기 조회 실패. type={}", e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public boolean hasSalesQuarterWithTtl(String quarterCode, Duration minimumTtl) {
        try {
            Long remainingMillis = redisTemplate.execute(
                    PUBLISHED_QUARTER_TTL, List.of(salesKey(quarterCode)), READY_FIELD
            );
            return remainingMillis != null && remainingMillis > minimumTtl.toMillis();
        } catch (DataAccessException e) {
            log.warn("추정매출 캐시 만료 시간 조회 실패. quarter={}, type={}", quarterCode, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public boolean publishLatestSalesQuarter(String quarterCode) {
        try {
            Long updated = redisTemplate.execute(PUBLISH_LATEST_QUARTER, List.of(LATEST_SALES_QUARTER_KEY), quarterCode);
            return Long.valueOf(1L).equals(updated);
        } catch (DataAccessException e) {
            log.warn("추정매출 최신 분기 전환 실패. quarter={}, type={}", quarterCode, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public void saveSalesQuarter(String quarterCode, Map<String, EstimatedSalesStatistics> statisticsByField) {
        String key = salesKey(quarterCode);
        String temporaryKey = key + ":loading:" + UUID.randomUUID();
        Duration ttl = statisticsByField.isEmpty() ? EMPTY_SALES_TTL : SALES_TTL;

        boolean published = false;

        try {
            Long started = redisTemplate.execute(
                    START_WRITE, List.of(temporaryKey), READY_FIELD,
                    Long.toString(TEMPORARY_KEY_TTL.toMillis())
            );
            if (!Long.valueOf(1L).equals(started)) {
                throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
            }

            HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
            Map<String, String> batch = new HashMap<>();

            for (Map.Entry<String, EstimatedSalesStatistics> entry : statisticsByField.entrySet()) {
                batch.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
                if (batch.size() == WRITE_BATCH_SIZE) {
                    hashOperations.putAll(temporaryKey, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                hashOperations.putAll(temporaryKey, batch);
            }

            Long result = redisTemplate.execute(
                    PUBLISH, List.of(temporaryKey, key), READY_FIELD,
                    Long.toString(statisticsByField.size() + 1L), Long.toString(ttl.toMillis())
            );
            if (!Long.valueOf(1L).equals(result)) {
                throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
            }

            published = true;
        } catch (DataAccessException | JacksonException e) {
            log.warn("추정매출 캐시 저장 실패. quarter={}, type={}", quarterCode, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        } finally {
            if (!published) {
                try {
                    redisTemplate.delete(temporaryKey);
                } catch (DataAccessException e) {
                    log.warn("추정매출 임시 캐시 삭제 실패. quarter={}", quarterCode);
                }
            }
        }
    }

    public static String salesField(String commercialAreaCode, String industryCode) {
        return commercialAreaCode + ":" + industryCode;
    }

    private String salesKey(String quarterCode) {
        return SALES_KEY_PREFIX + "{" + quarterCode + "}";
    }

    public record SalesLookup(boolean loaded, Optional<EstimatedSalesStatistics> statistics) {}
}
