package com.irion.irion_backend.domain.commercialStatistics.repository;

import com.irion.irion_backend.domain.commercialStatistics.dto.EstimatedSalesStatistics;
import com.irion.irion_backend.domain.commercialStatistics.dto.StoreStatistics;
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
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.SALES;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.STORES;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CommercialStatisticsCacheRepository {

    private static final String KEY_PREFIX = "commercial-stats:v1:";
    private static final String READY_FIELD = "__ready__";
    private static final int WRITE_BATCH_SIZE = 1000;

    private static final Duration SNAPSHOT_TTL = Duration.ofDays(14);
    private static final Duration EMPTY_SNAPSHOT_TTL = Duration.ofMinutes(15);
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
        var cached = findSnapshot(quarterKey(SALES, quarterCode), salesField(commercialAreaCode, industryCode),
                EstimatedSalesStatistics.class, acceptEmptyQuarter);
        return new SalesLookup(cached.loaded(), cached.statistics());
    }

    public <T> SnapshotLookup<T> findQuarterStatistics(
            StatisticsDataset dataset, String quarterCode, String field, Class<T> type
    ) {
        return findSnapshot(quarterKey(dataset, quarterCode), field, type, false);
    }

    public SnapshotLookup<StoreStatistics> findStoreStatistics(String quarterCode, String areaCode, String industryCode) {
        return findSnapshot(storeAreaKey(quarterCode, areaCode), industryCode, StoreStatistics.class, true);
    }

    private <T> SnapshotLookup<T> findSnapshot(String key, String field, Class<T> type, boolean acceptEmptySnapshot) {
        try {
            HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
            List<String> values = hashOperations.multiGet(key, List.of(READY_FIELD, field));

            String readyState = values.get(0);
            if (!"1".equals(readyState) && !(acceptEmptySnapshot && "empty".equals(readyState))) {
                return new SnapshotLookup<>(false, Optional.empty());
            }

            String json = values.get(1);
            if (json == null) {
                return new SnapshotLookup<>(true, Optional.empty());
            }

            T statistics = objectMapper.readValue(json, type);
            if (statistics == null) {
                throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
            }

            return new SnapshotLookup<>(true, Optional.of(statistics));
        } catch (DataAccessException | JacksonException e) {
            log.warn("상권 통계 캐시 조회 실패. key={}, type={}", key, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public Optional<String> findLatestSalesQuarter() {
        return findLatestQuarter(SALES);
    }

    public Optional<String> findLatestQuarter(StatisticsDataset dataset) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(latestQuarterKey(dataset)));
        } catch (DataAccessException e) {
            log.warn("상권 통계 최신 분기 조회 실패. dataset={}, type={}", dataset, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public boolean hasSalesQuarterWithTtl(String quarterCode, Duration minimumTtl) {
        return hasQuarterWithTtl(SALES, quarterCode, minimumTtl);
    }

    public boolean hasQuarterWithTtl(StatisticsDataset dataset, String quarterCode, Duration minimumTtl) {
        try {
            Long remainingMillis = redisTemplate.execute(
                    PUBLISHED_QUARTER_TTL, List.of(quarterKey(dataset, quarterCode)), READY_FIELD
            );
            return remainingMillis != null && remainingMillis > minimumTtl.toMillis();
        } catch (DataAccessException e) {
            log.warn("상권 통계 캐시 만료 시간 조회 실패. dataset={}, quarter={}", dataset, quarterCode);
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public boolean publishLatestSalesQuarter(String quarterCode) {
        return publishLatestQuarter(SALES, quarterCode);
    }

    public boolean publishLatestQuarter(StatisticsDataset dataset, String quarterCode) {
        try {
            Long updated = redisTemplate.execute(PUBLISH_LATEST_QUARTER, List.of(latestQuarterKey(dataset)), quarterCode);
            return Long.valueOf(1L).equals(updated);
        } catch (DataAccessException e) {
            log.warn("상권 통계 최신 분기 전환 실패. dataset={}, quarter={}", dataset, quarterCode);
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public void saveSalesQuarter(String quarterCode, Map<String, EstimatedSalesStatistics> statisticsByField) {
        saveQuarter(SALES, quarterCode, statisticsByField);
    }

    public <T> void saveQuarter(StatisticsDataset dataset, String quarterCode, Map<String, T> statisticsByField) {
        saveSnapshot(quarterKey(dataset, quarterCode), statisticsByField);
    }

    public void saveStoreArea(String quarterCode, String areaCode, Map<String, StoreStatistics> statisticsByIndustry) {
        saveSnapshot(storeAreaKey(quarterCode, areaCode), statisticsByIndustry);
    }

    private void saveSnapshot(String key, Map<String, ?> statisticsByField) {
        String temporaryKey = key + ":loading:" + UUID.randomUUID();
        Duration ttl = statisticsByField.isEmpty() ? EMPTY_SNAPSHOT_TTL : SNAPSHOT_TTL;

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

            for (var entry : statisticsByField.entrySet()) {
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
            log.warn("상권 통계 캐시 저장 실패. key={}, type={}", key, e.getClass().getSimpleName());
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        } finally {
            if (!published) {
                try {
                    redisTemplate.delete(temporaryKey);
                } catch (DataAccessException e) {
                    log.warn("상권 통계 임시 캐시 삭제 실패. key={}", key);
                }
            }
        }
    }

    public static String salesField(String commercialAreaCode, String industryCode) {
        return commercialAreaCode + ":" + industryCode;
    }

    public DatasetState findDatasetState(StatisticsDataset dataset) {
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(List.of(
                    latestQuarterKey(dataset), refreshFailureKey(dataset)
            ));
            return new DatasetState(values.get(0), values.get(1) != null);
        } catch (DataAccessException e) {
            log.warn("상권 통계 갱신 상태 조회 실패. dataset={}", dataset);
            throw new BusinessException(COMMERCIAL_STATS_CACHE_FAILED);
        }
    }

    public void recordRefreshSuccess(StatisticsDataset dataset) {
        try {
            redisTemplate.delete(refreshFailureKey(dataset));
        } catch (DataAccessException e) {
            log.warn("상권 통계 갱신 상태 삭제 실패. dataset={}", dataset);
        }
    }

    public void recordRefreshFailure(StatisticsDataset dataset) {
        try {
            redisTemplate.opsForValue().set(refreshFailureKey(dataset), "1");
        } catch (DataAccessException e) {
            log.warn("상권 통계 갱신 실패 상태 저장 실패. dataset={}", dataset);
        }
    }

    private String latestQuarterKey(StatisticsDataset dataset) {
        return datasetPrefix(dataset) + "latest-ready-quarter";
    }

    private String refreshFailureKey(StatisticsDataset dataset) {
        return datasetPrefix(dataset) + "last-refresh-failed";
    }

    private String quarterKey(StatisticsDataset dataset, String quarterCode) {
        return datasetPrefix(dataset) + "{" + quarterCode + "}";
    }

    private String storeAreaKey(String quarterCode, String areaCode) {
        return quarterKey(STORES, quarterCode) + ":" + areaCode;
    }

    private String datasetPrefix(StatisticsDataset dataset) {
        return KEY_PREFIX + dataset.cacheName() + ":";
    }

    public record SalesLookup(boolean loaded, Optional<EstimatedSalesStatistics> statistics) {}

    public record SnapshotLookup<T>(boolean loaded, Optional<T> statistics) {}

    public record DatasetState(String quarterCode, boolean refreshFailed) {}
}
