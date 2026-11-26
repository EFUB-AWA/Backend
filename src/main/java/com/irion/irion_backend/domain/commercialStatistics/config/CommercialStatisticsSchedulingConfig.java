package com.irion.irion_backend.domain.commercialStatistics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialSalesRefreshService;
import com.irion.irion_backend.domain.commercialStatistics.service.CommercialStatisticsRefreshService;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seoul-open-api", name = "refresh-enabled", havingValue = "true")
public class CommercialStatisticsSchedulingConfig {
    private final CommercialSalesRefreshService refreshSalesService;
    private final CommercialStatisticsRefreshService refreshStatisticsService;

    @Scheduled(initialDelayString = "PT0S", fixedDelayString = "${seoul-open-api.refresh-interval:PT24H}")
    void refreshSales() {
        refreshSalesService.refreshSales();
    }

    @Scheduled(initialDelayString = "PT0S", fixedDelayString = "${seoul-open-api.refresh-interval:PT24H}")
    void refreshStatistics() {
        refreshStatisticsService.refreshStatistics();
    }
}
