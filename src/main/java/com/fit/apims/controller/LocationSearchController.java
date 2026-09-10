package com.fit.apims.controller;

import java.util.Map;

import com.fit.apims.model.LocationSearchResult;
import com.fit.apims.service.GeocodingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationSearchController {

    private final GeocodingService geocodingService;

    public LocationSearchController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2 || query.length() > 120) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Enter a Malaysian location between 2 and 120 characters."));
        }

        try {
            return geocodingService.searchMalaysia(query)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                            "message", "No matching Malaysian location was found.")));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(Map.of(
                    "message", "Location search is temporarily unavailable."));
        }
    }
}
