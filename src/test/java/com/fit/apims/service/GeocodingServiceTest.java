package com.fit.apims.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.fit.apims.config.GeocodingProperties;
import com.fit.apims.model.LocationSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeocodingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsFirstNominatimResultIntoLocation() throws Exception {
        JsonNode response = objectMapper.readTree("""
                [
                  {
                    "display_name": "Damansara Damai, Petaling Jaya, Selangor, Malaysia",
                    "lat": "3.1987",
                    "lon": "101.5924"
                  }
                ]
                """);

        GeocodingService service = new GeocodingService(
                RestClient.create(),
                new GeocodingProperties(
                        "https://example.invalid/search",
                        "RainCheck-APIMS-Test/1.0",
                        Duration.ofHours(1),
                        Duration.ofSeconds(1)));

        LocationSearchResult result = service.mapFirstResult(response).orElseThrow();

        assertThat(result.name()).contains("Damansara Damai");
        assertThat(result.latitude()).isEqualTo(3.1987);
        assertThat(result.longitude()).isEqualTo(101.5924);
    }

    @Test
    void returnsEmptyWhenNominatimHasNoMatches() throws Exception {
        JsonNode response = objectMapper.readTree("[]");

        GeocodingService service = new GeocodingService(
                RestClient.create(),
                new GeocodingProperties(
                        "https://example.invalid/search",
                        "RainCheck-APIMS-Test/1.0",
                        Duration.ofHours(1),
                        Duration.ofSeconds(1)));

        assertThat(service.mapFirstResult(response)).isEmpty();
    }
}
