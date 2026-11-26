package com.irion.irion_backend.domain.commercialStatistics.service;

import com.irion.irion_backend.domain.commercialStatistics.config.SeoulOpenApiProperties;
import com.irion.irion_backend.domain.commercialStatistics.repository.CommercialStatisticsCacheRepository;
import com.irion.irion_backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

import static com.irion.irion_backend.domain.commercialStatistics.exception.CommercialStatisticsErrorCode.SALES_QUARTER_NOT_READY;
import static com.irion.irion_backend.domain.commercialStatistics.repository.StatisticsDataset.SALES;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsQuarters.SEOUL;
import static com.irion.irion_backend.domain.commercialStatistics.service.SeoulStatisticsQuarters.findLatestPublishedQuarter;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialSalesRefreshService {

    private final CommercialSalesService commercialSalesService;
    private final CommercialStatisticsCacheRepository cacheRepository;
    private final SeoulOpenApiProperties properties;

    public void refreshSales() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return;
        }

        try {
            refreshSales(LocalDate.now(SEOUL));
            cacheRepository.recordRefreshSuccess(SALES);
        } catch (BusinessException e) {
            cacheRepository.recordRefreshFailure(SALES);
            log.warn("추정매출 최신 분기 갱신 실패. code={}", e.getErrorCode().getCode());
        }
    }

    void refreshSales(LocalDate today) {
        Optional<String> currentQuarter = cacheRepository.findLatestSalesQuarter();
        String quarterCode = findLatestPublishedQuarter(today, commercialSalesService::isSalesQuarterPublished)
                .orElseThrow(() -> new BusinessException(SALES_QUARTER_NOT_READY));
        if (currentQuarter.isPresent() && quarterCode.compareTo(currentQuarter.get()) < 0) {
            log.warn("서울시 API가 현재 제공 중인 추정매출 분기보다 오래된 분기를 반환했습니다. current={}, found={}",
                    currentQuarter.get(), quarterCode);
            throw new BusinessException(SALES_QUARTER_NOT_READY);
        }

        commercialSalesService.prepareSalesQuarter(quarterCode);
        if (currentQuarter.filter(quarterCode::equals).isEmpty()
                && cacheRepository.publishLatestSalesQuarter(quarterCode)) {
            log.info("추정매출 최신 분기 준비 완료. quarter={}", quarterCode);
        }
    }

}
