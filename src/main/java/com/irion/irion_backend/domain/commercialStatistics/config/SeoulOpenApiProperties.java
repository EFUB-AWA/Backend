package com.irion.irion_backend.domain.commercialStatistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "seoul-open-api")
public record SeoulOpenApiProperties(
        String apiKey,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    @Override
    public String toString() {
        return "SeoulOpenApiProperties[baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
