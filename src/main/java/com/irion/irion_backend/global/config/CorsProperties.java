package com.irion.irion_backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
