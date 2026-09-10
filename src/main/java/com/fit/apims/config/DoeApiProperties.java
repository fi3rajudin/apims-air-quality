package com.fit.apims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doe.apims")
public record DoeApiProperties(
        String url,
        Duration cacheTtl) {
}