package com.fit.apims.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fit.apims.config.GeocodingProperties;
import com.fit.apims.model.LocationSearchResult;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class GeocodingService {

    private record CacheEntry(LocationSearchResult result, Instant expiresAt) {}

    private final RestClient restClient;
    private final GeocodingProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Object rateLimitLock = new Object();
    private Instant lastRequestAt = Instant.EPOCH;

    public GeocodingService(RestClient restClient, GeocodingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Optional<LocationSearchResult> searchMalaysia(String query) {
        String normalized = normalizeQuery(query);
        CacheEntry cached = cache.get(normalized);
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Optional.of(cached.result());
        }

        waitForRateLimit();

        var uri = UriComponentsBuilder.fromUriString(properties.url())
                .queryParam("q", query.trim())
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("countrycodes", "my")
                .queryParam("addressdetails", 1)
                .build()
                .encode()
                .toUri();

        JsonNode body = restClient.get()
                .uri(uri)
                .header(HttpHeaders.USER_AGENT, userAgent())
                .retrieve()
                .body(JsonNode.class);

        Optional<LocationSearchResult> result = mapFirstResult(body);
        result.ifPresent(value -> cache.put(
                normalized,
                new CacheEntry(value, Instant.now().plus(cacheTtl()))));
        return result;
    }

    Optional<LocationSearchResult> mapFirstResult(JsonNode body) {
        if (body == null || !body.isArray() || body.size() == 0) {
            return Optional.empty();
        }

        JsonNode first = body.get(0);
        String name = first.path("display_name").asText("").trim();
        String latText = first.path("lat").asText("").trim();
        String lonText = first.path("lon").asText("").trim();

        try {
            double latitude = Double.parseDouble(latText);
            double longitude = Double.parseDouble(lonText);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                return Optional.empty();
            }
            return Optional.of(new LocationSearchResult(
                    name.isBlank() ? "Selected location" : name,
                    latitude,
                    longitude));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String normalizeQuery(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private Duration cacheTtl() {
        Duration configured = properties.cacheTtl();
        return configured == null || configured.isNegative() || configured.isZero()
                ? Duration.ofHours(24)
                : configured;
    }

    private Duration minRequestInterval() {
        Duration configured = properties.minRequestInterval();
        return configured == null || configured.isNegative() || configured.isZero()
                ? Duration.ofMillis(1100)
                : configured;
    }

    private String userAgent() {
        String configured = properties.userAgent();
        return configured == null || configured.isBlank()
                ? "RainCheck-APIMS/1.0"
                : configured.trim();
    }

    private void waitForRateLimit() {
        synchronized (rateLimitLock) {
            Duration interval = minRequestInterval();
            Instant now = Instant.now();
            Instant allowedAt = lastRequestAt.plus(interval);

            if (now.isBefore(allowedAt)) {
                try {
                    Thread.sleep(Duration.between(now, allowedAt).toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Location search was interrupted", ex);
                }
            }

            lastRequestAt = Instant.now();
        }
    }
}
