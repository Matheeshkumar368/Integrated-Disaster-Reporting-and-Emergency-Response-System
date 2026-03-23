package com.disaster.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.disaster.management.entity.RescueTask;

@Repository
public interface RescueTaskRepository extends JpaRepository<RescueTask, Long> {
    
    // Find tasks assigned to a specific responder
    List<RescueTask> findByAssignedTo(String assignedTo);
    
    // Find tasks by zone
    List<RescueTask> findByZone(String zone);
    
    // Find tasks by status
    List<RescueTask> findByStatus(String status);
    
    // Find tasks by priority
    List<RescueTask> findByPriority(String priority);
    
    // Find tasks by assigned to and status
    List<RescueTask> findByAssignedToAndStatus(String assignedTo, String status);
    
    // Find tasks created by admin
    List<RescueTask> findByCreatedBy(String createdBy);
    
    // Find all tasks ordered by priority and deadline
    List<RescueTask> findAllByOrderByPriorityDescDeadlineAsc();
    
    // Find tasks by zone and status
    List<RescueTask> findByZoneAndStatus(String zone, String status);
}
