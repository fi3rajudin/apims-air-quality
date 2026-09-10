package com.fit.apims.model;

import java.time.OffsetDateTime;

public record AirQualityReading(
        String stationId,
        OffsetDateTime readingTime,
        Integer api,
        String status,
        String state,
        String region,
        String stationLocation,
        Double longitude,
        Double latitude,
        String place,
        String stationCategory,
        String pollutant) {
}
