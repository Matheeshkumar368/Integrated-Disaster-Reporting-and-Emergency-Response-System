package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.Disaster;
import com.disaster.management.service.DisasterService;
import com.disaster.management.service.ImdWeatherService;

@RestController
@RequestMapping("/api")
public class DisasterController {

    private static final Logger log = LoggerFactory.getLogger(DisasterController.class);

    @Autowired
    private DisasterService disasterService;

    @Autowired
    private ImdWeatherService imdWeatherService;

    @GetMapping("/disasters")
    public List<Disaster> getDisasters() {
        return disasterService.getAllDisasters();
    }

    /**
     * Unified weather observation endpoint.
     * Supports querying by city name or geographic coordinates (lat/lon).
     * Connects with IMD (official) or WMO Global Observation Network (transparent fallback).
     */
    @GetMapping("/weather")
    public ResponseEntity<Map<String, Object>> getWeather(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {

        double targetLat = 13.0827; // Default Chennai
        double targetLon = 80.2707;
        String resolvedCity = (city != null && !city.trim().isEmpty()) ? city.trim() : "Chennai";

        if (lat != null && lon != null) {
            targetLat = lat;
            targetLon = lon;
            resolvedCity = String.format("%.2f, %.2f", lat, lon);
        } else if (city != null) {
            // Coordinate mapping for major Indian cities
            Map<String, double[]> cityMap = Map.of(
                "chennai", new double[]{13.0827, 80.2707},
                "coimbatore", new double[]{11.0168, 76.9558},
                "madurai", new double[]{9.9252, 78.1198},
                "tiruchirappalli", new double[]{10.7905, 78.7047},
                "salem", new double[]{11.6643, 78.1460},
                "tirunelveli", new double[]{8.7139, 77.7567},
                "dindigul", new double[]{10.3687, 77.9803},
                "delhi", new double[]{28.6139, 77.2090},
                "mumbai", new double[]{19.0760, 72.8777},
                "kolkata", new double[]{22.5726, 88.3639}
            );

            double[] coords = cityMap.get(resolvedCity.toLowerCase());
            if (coords != null) {
                targetLat = coords[0];
                targetLon = coords[1];
            }
        }

        ImdWeatherService.WeatherObservation obs = imdWeatherService.getWeather(targetLat, targetLon);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("city", resolvedCity);
        response.put("latitude", targetLat);
        response.put("longitude", targetLon);
        response.put("source", obs.sourceName());
        response.put("isOfficialImd", obs.isOfficialImd());
        response.put("temperature", obs.temperatureC());
        response.put("humidity", obs.humidityPercent());
        response.put("windSpeed", obs.windSpeedKmh());
        response.put("precipitation", obs.precipitationMm());
        response.put("condition", obs.conditionText());
        response.put("timestamp", obs.observationTime());

        return ResponseEntity.ok(response);
    }
}
