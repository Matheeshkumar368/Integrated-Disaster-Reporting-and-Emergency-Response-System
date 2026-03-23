package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.Alert;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    List<Alert> findByRegion(String region);
    
    List<Alert> findByStatus(String status);
    
    List<Alert> findByRegionAndStatus(String region, String status);
    
    List<Alert> findByBroadcastedTrueOrderByCreatedAtDesc();
    
    List<Alert> findByStatusAndBroadcastedTrueOrderByCreatedAtDesc(String status);
}
