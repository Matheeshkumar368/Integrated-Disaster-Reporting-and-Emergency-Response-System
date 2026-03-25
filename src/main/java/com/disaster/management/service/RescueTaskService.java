package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.repository.RescueTaskRepository;

@Service
public class RescueTaskService {

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    // Create a new rescue task with validation
    @Transactional
    public RescueTask createTask(RescueTask task) {
        // Validate required fields
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (task.getZone() == null || task.getZone().trim().isEmpty()) {
            throw new IllegalArgumentException("Task zone is required");
        }
        if (task.getPriority() == null || task.getPriority().trim().isEmpty()) {
            throw new IllegalArgumentException("Task priority is required");
        }
        // Validate priority values
        String priority = task.getPriority();
        if (!priority.equals("High") && !priority.equals("Medium") && !priority.equals("Low")) {
            throw new IllegalArgumentException("Invalid priority value. Must be High, Medium, or Low");
        }
        task.setCreatedAt(LocalDateTime.now());
        task.setStatus("Pending");
        return rescueTaskRepository.save(task);
    }

    // Get all tasks
    public List<RescueTask> getAllTasks() {
        return rescueTaskRepository.findAll();
    }

    // Get task by ID
    public Optional<RescueTask> getTaskById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        return rescueTaskRepository.findById(id);
    }

    // Get tasks assigned to a responder
    public List<RescueTask> getTasksByResponder(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Responder email cannot be empty");
        }
        return rescueTaskRepository.findByAssignedTo(email);
    }

    // Get tasks by zone
    public List<RescueTask> getTasksByZone(String zone) {
        if (zone == null || zone.trim().isEmpty()) {
            throw new IllegalArgumentException("Zone cannot be empty");
        }
        return rescueTaskRepository.findByZone(zone);
    }

    // Get tasks by status
    public List<RescueTask> getTasksByStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }
        return rescueTaskRepository.findByStatus(status);
    }

    // Get tasks by priority
    public List<RescueTask> getTasksByPriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            throw new IllegalArgumentException("Priority cannot be empty");
        }
        return rescueTaskRepository.findByPriority(priority);
    }

    // Get tasks ordered by priority and deadline
    public List<RescueTask> getAllTasksSorted() {
        return rescueTaskRepository.findAllByOrderByPriorityDescDeadlineAsc();
    }

    // Update task status with validation
    @Transactional
    public RescueTask updateTaskStatus(Long taskId, String status, String notes) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }
        // Validate status values - allow more flexible statuses
        String[] validStatuses = {"Pending", "In Progress", "Completed", "Accepted", "Rejected"};
        boolean isValid = false;
        for (String validStatus : validStatuses) {
            if (validStatus.equals(status)) {
                isValid = true;
                break;
            }
        }
        if (!isValid) {
            throw new IllegalArgumentException("Invalid status value. Must be: Pending, In Progress, Completed, Accepted, or Rejected");
        }
        
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found with ID: " + taskId);
        }
        RescueTask task = taskOpt.get();
        task.setStatus(status);
        if (notes != null && !notes.isEmpty()) {
            String existingNotes = task.getNotes();
            if (existingNotes == null) existingNotes = "";
            task.setNotes(existingNotes + "\n[" + LocalDateTime.now() + "] " + notes);
        }
        return rescueTaskRepository.save(task);
    }

    // Assign task to responder with validation
    @Transactional
    public RescueTask assignTask(Long taskId, String responderEmail, String responderName) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (responderEmail == null || responderEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Responder email cannot be empty");
        }
        if (responderName == null || responderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Responder name cannot be empty");
        }
        
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found with ID: " + taskId);
        }
        RescueTask task = taskOpt.get();
        task.setAssignedTo(responderEmail);
        task.setAssignedResponderName(responderName);
        return rescueTaskRepository.save(task);
    }

    // Update task with validation
    @Transactional
    public RescueTask updateTask(RescueTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (task.getTaskId() == null) {
            throw new IllegalArgumentException("Task ID cannot be null for update");
        }
        // Verify task exists
        if (!rescueTaskRepository.existsById(task.getTaskId())) {
            throw new IllegalArgumentException("Task not found with ID: " + task.getTaskId());
        }
        return rescueTaskRepository.save(task);
    }

    // Delete task
    @Transactional
    public void deleteTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        if (!rescueTaskRepository.existsById(id)) {
            throw new IllegalArgumentException("Task not found with ID: " + id);
        }
        rescueTaskRepository.deleteById(id);
    }

    // Get responder tasks by status
    public List<RescueTask> getResponderTasksByStatus(String email, String status) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Responder email cannot be empty");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }
        return rescueTaskRepository.findByAssignedToAndStatus(email, status);
    }

    // Get tasks by zone and status
    public List<RescueTask> getTasksByZoneAndStatus(String zone, String status) {
        if (zone == null || zone.trim().isEmpty()) {
            throw new IllegalArgumentException("Zone cannot be empty");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }
        return rescueTaskRepository.findByZoneAndStatus(zone, status);
    }
}
