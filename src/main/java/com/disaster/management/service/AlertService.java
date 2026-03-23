package com.disaster.management.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.Alert;
import com.disaster.management.repository.AlertRepository;

@Service
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    public Alert createAlert(Alert alert) {
        return alertRepository.save(alert);
    }

    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    public List<Alert> getAlertsByRegion(String region) {
        return alertRepository.findByRegion(region);
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByStatus("Active");
    }

    public Alert acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setStatus("Acknowledged");
            return alertRepository.save(alert);
        }
        return null;
    }

    public Alert resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert != null) {
            alert.setStatus("Resolved");
            return alertRepository.save(alert);
        }
        return null;
    }
    
    public List<Alert> getBroadcastedAlerts() {
        return alertRepository.findByBroadcastedTrueOrderByCreatedAtDesc();
    }
    
    public Alert getAlertById(Long alertId) {
        return alertRepository.findById(alertId).orElse(null);
    }
    
    public Alert updateAlert(Alert alert) {
        return alertRepository.save(alert);
    }
}
