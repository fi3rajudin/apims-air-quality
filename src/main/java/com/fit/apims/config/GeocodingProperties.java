package com.fit.apims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geocoding")
public record GeocodingProperties(
        String url,
        String userAgent,
        Duration cacheTtl,
        Duration minRequestInterval) {
}
