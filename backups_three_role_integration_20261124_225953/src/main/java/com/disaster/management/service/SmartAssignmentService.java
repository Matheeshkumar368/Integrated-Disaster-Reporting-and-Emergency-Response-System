package com.disaster.management.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.TrackingSession;
import com.disaster.management.entity.User;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.TrackingSessionRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class SmartAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(SmartAssignmentService.class);

    // Approximate known district coordinate anchors for Tamil Nadu & India zones
    private static final Map<String, double[]> ZONE_COORDINATES = new HashMap<>();

    static {
        ZONE_COORDINATES.put("chennai", new double[]{13.0827, 80.2707});
        ZONE_COORDINATES.put("coimbatore", new double[]{11.0168, 76.9558});
        ZONE_COORDINATES.put("madurai", new double[]{9.9252, 78.1198});
        ZONE_COORDINATES.put("tiruchirappalli", new double[]{10.7905, 78.7047});
        ZONE_COORDINATES.put("salem", new double[]{11.6643, 78.1460});
        ZONE_COORDINATES.put("tirunelveli", new double[]{8.7139, 77.7567});
        ZONE_COORDINATES.put("kanyakumari", new double[]{8.0883, 77.5385});
        ZONE_COORDINATES.put("vellore", new double[]{12.9165, 79.1325});
        ZONE_COORDINATES.put("cuddalore", new double[]{11.7480, 79.7714});
        ZONE_COORDINATES.put("bengaluru", new double[]{12.9716, 77.5946});
        ZONE_COORDINATES.put("delhi", new double[]{28.6139, 77.2090});
        ZONE_COORDINATES.put("mumbai", new double[]{19.0760, 72.8777});
        ZONE_COORDINATES.put("kolkata", new double[]{22.5726, 88.3639});
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RescueTaskRepository taskRepository;

    @Autowired
    private TrackingSessionRepository sessionRepository;

    @Autowired
    private RouteEstimationService routeService;

    public List<Map<String, Object>> getRecommendationsForTask(Long taskId) {
        Optional<RescueTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found with ID: " + taskId);
        }

        RescueTask task = taskOpt.get();
        Double targetLat = task.getLatitude();
        Double targetLng = task.getLongitude();
        String zone = task.getZone();
        String priority = task.getPriority();
        String title = task.getTitle();

        if (targetLat == null || targetLng == null) {
            if (zone != null) {
                double[] zoneCoords = getCoordinatesForZone(zone);
                if (zoneCoords != null) {
                    targetLat = zoneCoords[0];
                    targetLng = zoneCoords[1];
                }
            }
        }

        return evaluateResponders(targetLat, targetLng, zone, priority, title);
    }

    public List<Map<String, Object>> evaluateResponders(
            Double targetLat,
            Double targetLng,
            String zone,
            String priority,
            String taskDescription) {

        List<User> responders = userRepository.findByRole("Responder");
        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (User responder : responders) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("responderId", responder.getId());
            rec.put("responderEmail", responder.getEmail());
            rec.put("responderName", responder.getName());
            rec.put("phone", responder.getPhone());
            rec.put("assignedZone", responder.getAssignedZone());
            rec.put("classification", responder.getGroupClassification() != null ? responder.getGroupClassification() : "Rescuer");

            // 1. Workload Check
            long activeTasks = taskRepository.countByAssignedToAndStatusIn(
                responder.getEmail(),
                Arrays.asList("Pending", "In Progress")
            );
            rec.put("activeTasks", activeTasks);

            int workloadScore = 0;
            if (activeTasks == 0) workloadScore = 30;
            else if (activeTasks == 1) workloadScore = 20;
            else if (activeTasks == 2) workloadScore = 10;
            else workloadScore = 0;

            // 2. Proximity Check
            Double responderLat = null;
            Double responderLng = null;

            // Try active tracking session first
            List<TrackingSession> activeSessions = sessionRepository.findByResponderEmailAndStatus(responder.getEmail(), "ACTIVE");
            if (!activeSessions.isEmpty() && activeSessions.get(0).getCurrentLatitude() != null) {
                responderLat = activeSessions.get(0).getCurrentLatitude();
                responderLng = activeSessions.get(0).getCurrentLongitude();
            } else if (responder.getAssignedZone() != null) {
                double[] coords = getCoordinatesForZone(responder.getAssignedZone());
                if (coords != null) {
                    responderLat = coords[0];
                    responderLng = coords[1];
                }
            }

            double distanceKm = -1.0;
            int proximityScore = 10; // Default baseline

            if (targetLat != null && targetLng != null && responderLat != null && responderLng != null) {
                distanceKm = routeService.calculateDistanceKm(targetLat, targetLng, responderLat, responderLng);
                distanceKm = Math.round(distanceKm * 10.0) / 10.0;

                if (distanceKm <= 5.0) proximityScore = 40;
                else if (distanceKm <= 15.0) proximityScore = 30;
                else if (distanceKm <= 35.0) proximityScore = 20;
                else if (distanceKm <= 60.0) proximityScore = 12;
                else proximityScore = 5;
            }
            rec.put("distanceKm", distanceKm > 0 ? distanceKm : null);

            // 3. Specialization & Role Matching (20 points max)
            int skillScore = 10;
            String group = responder.getGroupClassification() != null ? responder.getGroupClassification().toLowerCase() : "";
            String descLower = (taskDescription != null ? taskDescription : "").toLowerCase();

            if ("High".equalsIgnoreCase(priority) && group.contains("leader")) {
                skillScore = 20;
            } else if ((descLower.contains("flood") || descLower.contains("rescue") || descLower.contains("water")) && group.contains("rescuer")) {
                skillScore = 20;
            } else if ((descLower.contains("medical") || descLower.contains("injury") || descLower.contains("first aid")) && group.contains("helper")) {
                skillScore = 20;
            }

            // 4. Zone Alignment (10 points max)
            int zoneScore = 0;
            if (zone != null && responder.getAssignedZone() != null &&
                    zone.trim().equalsIgnoreCase(responder.getAssignedZone().trim())) {
                zoneScore = 10;
            }

            int totalScore = Math.min(100, workloadScore + proximityScore + skillScore + zoneScore);
            rec.put("matchScore", totalScore);

            // Badges
            List<String> badges = new ArrayList<>();
            if (activeTasks == 0) badges.add("AVAILABLE_NOW");
            if (distanceKm > 0 && distanceKm <= 15.0) badges.add("PROXIMITY_ALERT");
            if (zoneScore > 0) badges.add("LOCAL_ZONE");
            if (skillScore >= 20) badges.add("SPECIALIST_MATCH");
            rec.put("badges", badges);

            recommendations.add(rec);
        }

        // Sort descending by matchScore
        recommendations.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("matchScore")).reversed());

        // Assign ranks
        for (int i = 0; i < recommendations.size(); i++) {
            recommendations.get(i).put("rank", i + 1);
        }

        return recommendations;
    }

    private double[] getCoordinatesForZone(String zone) {
        if (zone == null) return null;
        String key = zone.toLowerCase().trim();
        for (Map.Entry<String, double[]> entry : ZONE_COORDINATES.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}

