package com.fit.apims.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fit.apims.model.AirQualityReading;
import com.fit.apims.service.DoeApimsService;

@RestController
@RequestMapping("/api/readings")
public class AirQualityController {

    private final DoeApimsService doeApimsService;

    public AirQualityController(DoeApimsService doeApimsService) {
        this.doeApimsService = doeApimsService;
    }

    @GetMapping
    public List<AirQualityReading> getReadings(
            @RequestParam(required = false) String state) {

        return doeApimsService.getCurrentReadings(state);
    }
}