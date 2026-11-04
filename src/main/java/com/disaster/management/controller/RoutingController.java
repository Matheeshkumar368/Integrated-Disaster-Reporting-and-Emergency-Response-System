package com.disaster.management.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.service.RouteEstimationService;

@RestController
@RequestMapping("/api/routing")
@CrossOrigin(origins = {"*"})
public class RoutingController {

    @Autowired
    private RouteEstimationService routeService;

    @GetMapping("/directions")
    public ResponseEntity<?> getDirections(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double endLat,
            @RequestParam double endLng) {
        Map<String, Object> route = routeService.calculateRoute(startLat, startLng, endLat, endLng);
        return ResponseEntity.ok(route);
    }

    @GetMapping("/distance")
    public ResponseEntity<?> getDistance(
            @RequestParam double lat1,
            @RequestParam double lon1,
            @RequestParam double lat2,
            @RequestParam double lon2) {
        double distanceKm = routeService.calculateDistanceKm(lat1, lon1, lat2, lon2);
        return ResponseEntity.ok(Map.of(
            "distanceKm", Math.round(distanceKm * 100.0) / 100.0,
            "distanceMeters", Math.round(distanceKm * 1000.0)
        ));
    }
}

