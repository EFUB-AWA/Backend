package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SALES_QUARTER_NOT_READY;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialSalesRefreshService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_CANDIDATE_QUARTERS = 8;

    private final CommercialSalesService commercialSalesService;
    private final CommercialStatisticsCacheRepository cacheRepository;
    private final SeoulOpenApiProperties properties;

    @Scheduled(initialDelayString = "PT0S", fixedDelayString = "${seoul-open-api.refresh-interval:PT24H}")
    public void refreshSales() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return;
        }

        try {
            refreshSales(LocalDate.now(SEOUL));
        } catch (BusinessException e) {
            log.warn("추정매출 최신 분기 갱신 실패. code={}", e.getErrorCode().getCode());
        }
    }

    void refreshSales(LocalDate today) {
        Optional<String> currentQuarter = cacheRepository.findLatestSalesQuarter();
        String quarterCode = findLatestPublishedQuarter(today);
        if (currentQuarter.isPresent() && quarterCode.compareTo(currentQuarter.get()) < 0) {
            log.warn("서울시 API가 현재 제공 중인 추정매출 분기보다 오래된 분기를 반환했습니다. current={}, found={}",
                    currentQuarter.get(), quarterCode);
            return;
        }

        commercialSalesService.prepareSalesQuarter(quarterCode);
        if (currentQuarter.filter(quarterCode::equals).isEmpty()
                && cacheRepository.publishLatestSalesQuarter(quarterCode)) {
            log.info("추정매출 최신 분기 준비 완료. quarter={}", quarterCode);
        }
    }

    private String findLatestPublishedQuarter(LocalDate today) {
        YearMonth quarterStart = YearMonth.from(today).minusMonths((today.getMonthValue() - 1) % 3);

        for (int i = 0; i < MAX_CANDIDATE_QUARTERS; i++) {
            YearMonth candidate = quarterStart.minusMonths(3L * i);
            int quarter = (candidate.getMonthValue() - 1) / 3 + 1;
            String quarterCode = Integer.toString(candidate.getYear()) + quarter;
            if (commercialSalesService.isSalesQuarterPublished(quarterCode)) {
                return quarterCode;
            }
        }

        throw new BusinessException(SALES_QUARTER_NOT_READY);
    }
}
