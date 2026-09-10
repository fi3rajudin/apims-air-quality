package com.fit.apims.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fit.apims.config.DoeApiProperties;
import com.fit.apims.model.AirQualityReading;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DoeApimsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsDoeAttributesIntoReadingAndPreservesMalaysiaWallClockTime() throws Exception {
        JsonNode attributes = objectMapper.readTree("""
                {
                  "STATION_ID": "CA01R",
                  "DATETIME": 1778270400000,
                  "API": 37,
                  "CLASS": "Good",
                  "STATE_NAME": "Perlis",
                  "REGION_NAME": "Northern",
                  "STATION_LOCATION": "Kangar, PERLIS",
                  "LONGITUDE": 100.210937,
                  "LATITUDE": 6.429928,
                  "PLACE": "Institut Latihan Perindustrian (Kangar)",
                  "STATION_CATEGORY": "Sub Urban",
                  "PARAM_SELECTED": "PM2.5"
                }
                """);

        DoeApimsService service = new DoeApimsService(
                RestClient.create(),
                new DoeApiProperties("https://example.invalid", Duration.ofMinutes(5)));

        AirQualityReading reading = service.mapReading(attributes);

        assertThat(reading.stationId()).isEqualTo("CA01R");
        assertThat(reading.api()).isEqualTo(37);
        assertThat(reading.status()).isEqualTo("Good");
        assertThat(reading.state()).isEqualTo("Perlis");
        assertThat(reading.pollutant()).isEqualTo("PM2.5");
        assertThat(reading.latitude()).isEqualTo(6.429928);
        assertThat(reading.longitude()).isEqualTo(100.210937);
        assertThat(reading.readingTime().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(reading.readingTime()).isInstanceOf(OffsetDateTime.class);
    }
}
