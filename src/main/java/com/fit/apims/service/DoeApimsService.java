package com.fit.apims.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fit.apims.config.DoeApiProperties;
import com.fit.apims.model.AirQualityReading;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class DoeApimsService {

    private static final ZoneOffset MALAYSIA_OFFSET = ZoneOffset.ofHours(8);

    private final RestClient restClient;
    private final DoeApiProperties properties;

    private volatile List<AirQualityReading> cachedReadings = List.of();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public DoeApimsService(RestClient restClient, DoeApiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public List<AirQualityReading> getCurrentReadings(String state) {
        List<AirQualityReading> readings = getCachedOrFetch();

        if (state == null || state.isBlank()) {
            return readings;
        }

        String wantedState = state.trim().toLowerCase(Locale.ROOT);
        return readings.stream()
                .filter(reading -> reading.state() != null)
                .filter(reading -> reading.state().trim().toLowerCase(Locale.ROOT).equals(wantedState))
                .toList();
    }

    private List<AirQualityReading> getCachedOrFetch() {
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt) && !cachedReadings.isEmpty()) {
            return cachedReadings;
        }

        synchronized (this) {
            now = Instant.now();
            if (now.isBefore(cacheExpiresAt) && !cachedReadings.isEmpty()) {
                return cachedReadings;
            }

            List<AirQualityReading> fresh = fetchFromDoe();
            cachedReadings = fresh;
            cacheExpiresAt = now.plus(cacheTtl());
            return fresh;
        }
    }

    private Duration cacheTtl() {
        Duration configured = properties.cacheTtl();
        return configured == null || configured.isNegative() || configured.isZero()
                ? Duration.ofMinutes(5)
                : configured;
    }

    private List<AirQualityReading> fetchFromDoe() {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.url())
                .queryParam("f", "json")
                .queryParam("outFields", "*")
                .queryParam("returnGeometry", false)
                .queryParam("spatialRel", "esriSpatialRelIntersects")
                .queryParam("where", "1=1")
                .build()
                .encode()
                .toUri();

        JsonNode root = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            return List.of();
        }

        if (root.has("error")) {
            throw new IllegalStateException("DOE APIMS returned an error: " + root.get("error"));
        }

        JsonNode features = root.path("features");
        if (!features.isArray()) {
            throw new IllegalStateException("Unexpected DOE APIMS response: missing features array");
        }

        return java.util.stream.StreamSupport.stream(features.spliterator(), false)
                .map(feature -> feature.path("attributes"))
                .filter(JsonNode::isObject)
                .map(this::mapReading)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        AirQualityReading::state,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(
                                AirQualityReading::stationLocation,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    AirQualityReading mapReading(JsonNode attributes) {
        Long epochMillis = longValue(attributes, "DATETIME", "DATE_TIME", "READING_TIME");

        return new AirQualityReading(
                text(attributes, "STATION_ID", "STATIONID"),
                epochMillis == null
                        ? null
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                                .atOffset(MALAYSIA_OFFSET),
                integer(attributes, "API", "API_VALUE"),
                text(attributes, "CLASS", "STATUS", "API_CLASS"),
                text(attributes, "STATE_NAME", "STATE"),
                text(attributes, "REGION_NAME", "REGION"),
                text(attributes, "STATION_LOCATION", "LOCATION"),
                decimal(attributes, "LONGITUDE", "LONG"),
                decimal(attributes, "LATITUDE", "LAT"),
                text(attributes, "PLACE", "PLACE_NAME"),
                text(attributes, "STATION_CATEGORY", "CATEGORY"),
                text(attributes, "PARAM_SELECTED", "PARAMETER", "POLLUTANT"));
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                if (value.isInt() || value.isLong() || value.isNumber()) {
                    return value.asInt();
                }
                try {
                    return Integer.valueOf(value.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asLong();
                }
                try {
                    return Long.valueOf(value.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private Double decimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asDouble();
                }
                try {
                    return Double.valueOf(value.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
}
