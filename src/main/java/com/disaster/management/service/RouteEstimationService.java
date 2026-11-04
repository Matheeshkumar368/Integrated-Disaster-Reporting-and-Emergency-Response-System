package com.disaster.management.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RouteEstimationService {

    private static final Logger log = LoggerFactory.getLogger(RouteEstimationService.class);

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double DEFAULT_EMERGENCY_SPEED_MPS = 9.72; // ~35 km/h in urban/disaster context

    @Value("${app.routing.osrm-url:https://router.project-osrm.org/route/v1/driving}")
    private String osrmBaseUrl;

    @Value("${app.routing.timeout-ms:3000}")
    private int timeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calculate driving route between origin and destination.
     * Uses OSRM first, with automatic Haversine fallback on network or service error.
     */
    public Map<String, Object> calculateRoute(double startLat, double startLng, double endLat, double endLng) {
        Map<String, Object> result = new HashMap<>();

        // Validate coordinates
        if (Double.isNaN(startLat) || Double.isNaN(startLng) || Double.isNaN(endLat) || Double.isNaN(endLng)
                || Math.abs(startLat) > 90 || Math.abs(endLat) > 90
                || Math.abs(startLng) > 180 || Math.abs(endLng) > 180) {
            result.put("status", "error");
            result.put("message", "Invalid coordinates provided.");
            return result;
        }

        try {
            // OSRM expects coordinates as: {lng1},{lat1};{lng2},{lat2}
            String urlString = String.format(Locale.US, "%s/%f,%f;%f,%f?overview=full&geometries=geojson",
                    osrmBaseUrl, startLng, startLat, endLng, endLat);

            URI uri = URI.create(urlString);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "DisasterManagementPlatform/2.0");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }

                    JsonNode root = objectMapper.readTree(responseBuilder.toString());
                    if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                        JsonNode route = root.path("routes").get(0);
                        double distanceMeters = route.path("distance").asDouble();
                        double durationSeconds = route.path("duration").asDouble();

                        List<List<Double>> coordinates = new ArrayList<>();
                        JsonNode coordsNode = route.path("geometry").path("coordinates");
                        if (coordsNode.isArray()) {
                            for (JsonNode pt : coordsNode) {
                                List<Double> pair = new ArrayList<>();
                                pair.add(pt.get(0).asDouble()); // lng
                                pair.add(pt.get(1).asDouble()); // lat
                                coordinates.add(pair);
                            }
                        }

                        result.put("status", "success");
                        result.put("source", "OSRM_ROAD_NETWORK");
                        result.put("distanceMeters", distanceMeters);
                        result.put("distanceKm", Math.round((distanceMeters / 1000.0) * 10.0) / 10.0);
                        result.put("durationSeconds", Math.round(durationSeconds));
                        result.put("durationMinutes", Math.max(1, (int) Math.ceil(durationSeconds / 60.0)));
                        result.put("etaText", formatEta(distanceMeters, durationSeconds));
                        result.put("coordinates", coordinates);
                        return result;
                    }
                }
            } else {
                log.warn("OSRM returned HTTP {}: Falling back to Haversine routing.", responseCode);
            }
        } catch (Exception e) {
            log.info("OSRM unreachable or timed out ({}): Using Haversine route estimation.", e.getMessage());
        }

        // Fallback to Haversine calculation
        return calculateHaversineFallback(startLat, startLng, endLat, endLng);
    }

    /**
     * Haversine great-circle distance with realistic disaster response road multiplier.
     */
    public Map<String, Object> calculateHaversineFallback(double lat1, double lon1, double lat2, double lon2) {
        Map<String, Object> result = new HashMap<>();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightDistanceMeters = EARTH_RADIUS_METERS * c;

        // In road networks, actual route is typically 1.25x - 1.35x longer than straight-line
        double estimatedRoadDistanceMeters = straightDistanceMeters * 1.30;
        double estimatedDurationSeconds = (estimatedRoadDistanceMeters / DEFAULT_EMERGENCY_SPEED_MPS);

        List<List<Double>> straightLine = new ArrayList<>();
        straightLine.add(List.of(lon1, lat1));
        straightLine.add(List.of(lon2, lat2));

        result.put("status", "success");
        result.put("source", "HAVERSINE_ESTIMATE");
        result.put("distanceMeters", Math.round(estimatedRoadDistanceMeters));
        result.put("distanceKm", Math.round((estimatedRoadDistanceMeters / 1000.0) * 10.0) / 10.0);
        result.put("durationSeconds", Math.round(estimatedDurationSeconds));
        result.put("durationMinutes", Math.max(1, (int) Math.ceil(estimatedDurationSeconds / 60.0)));
        result.put("etaText", formatEta(estimatedRoadDistanceMeters, estimatedDurationSeconds) + " (Estimated)");
        result.put("coordinates", straightLine);
        return result;
    }

    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (EARTH_RADIUS_METERS * c) / 1000.0;
    }

    private String formatEta(double distanceMeters, double durationSeconds) {
        int mins = Math.max(1, (int) Math.round(durationSeconds / 60.0));
        double km = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
        if (mins < 60) {
            return String.format(Locale.US, "%d mins (%s km)", mins, km);
        } else {
            int hrs = mins / 60;
            int remainingMins = mins % 60;
            return String.format(Locale.US, "%dh %02dm (%s km)", hrs, remainingMins, km);
        }
    }
}

