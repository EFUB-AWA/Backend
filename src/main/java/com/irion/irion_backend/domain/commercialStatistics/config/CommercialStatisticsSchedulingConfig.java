package com.irion.irion_backend.domain.commercialStatistics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "seoul-open-api", name = "refresh-enabled", havingValue = "true")
public class CommercialStatisticsSchedulingConfig {
}
