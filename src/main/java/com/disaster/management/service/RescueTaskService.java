package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class RescueTaskService {

    @Autowired
    private RescueTaskRepository rescueTaskRepository;
    
    @Autowired
    private UserRepository userRepository;

    // Create a new rescue task
    public RescueTask createTask(RescueTask task) {
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
        return rescueTaskRepository.findById(id);
    }

    // Get tasks assigned to a responder
    public List<RescueTask> getTasksByResponder(String email) {
        return rescueTaskRepository.findByAssignedTo(email);
    }

    // Get tasks by zone
    public List<RescueTask> getTasksByZone(String zone) {
        return rescueTaskRepository.findByZone(zone);
    }

    // Get tasks by status
    public List<RescueTask> getTasksByStatus(String status) {
        return rescueTaskRepository.findByStatus(status);
    }

    // Get tasks by priority
    public List<RescueTask> getTasksByPriority(String priority) {
        return rescueTaskRepository.findByPriority(priority);
    }

    // Get tasks ordered by priority and deadline
    public List<RescueTask> getAllTasksSorted() {
        return rescueTaskRepository.findAllByOrderByPriorityDescDeadlineAsc();
    }

    // Update task status
    public RescueTask updateTaskStatus(Long taskId, String status, String notes) {
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            RescueTask task = taskOpt.get();
            task.setStatus(status);
            if (notes != null && !notes.isEmpty()) {
                String existingNotes = task.getNotes();
                if (existingNotes == null) existingNotes = "";
                task.setNotes(existingNotes + "\n[" + LocalDateTime.now() + "] " + notes);
            }
            return rescueTaskRepository.save(task);
        }
        return null;
    }

    // Assign task to responder
    public RescueTask assignTask(Long taskId, String responderEmail, String responderName) {
        Optional<RescueTask> taskOpt = rescueTaskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            RescueTask task = taskOpt.get();
            task.setAssignedTo(responderEmail);
            task.setAssignedResponderName(responderName);
            return rescueTaskRepository.save(task);
        }
        return null;
    }

    // Update task
    public RescueTask updateTask(RescueTask task) {
        return rescueTaskRepository.save(task);
    }

    // Delete task
    public void deleteTask(Long id) {
        rescueTaskRepository.deleteById(id);
    }

    // Get responder tasks by status
    public List<RescueTask> getResponderTasksByStatus(String email, String status) {
        return rescueTaskRepository.findByAssignedToAndStatus(email, status);
    }

    // Get tasks by zone and status
    public List<RescueTask> getTasksByZoneAndStatus(String zone, String status) {
        return rescueTaskRepository.findByZoneAndStatus(zone, status);
    }
}
