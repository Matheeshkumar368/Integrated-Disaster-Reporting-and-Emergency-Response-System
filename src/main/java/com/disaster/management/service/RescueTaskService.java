package com.disaster.management.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;

@Service
public class RescueTaskService {

    private static final Logger log = LoggerFactory.getLogger(RescueTaskService.class);

    @Autowired
    private RescueTaskRepository rescueTaskRepository;

    @Autowired
    private CitizenReportRepository citizenReportRepository;

    @Autowired
    private UserRepository userRepository;

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
        // Normalize priority values
        String priority = task.getPriority();
        if (priority == null || priority.trim().isEmpty()) {
            task.setPriority("Medium");
        } else {
            String p = priority.trim();
            if (p.equalsIgnoreCase("Critical") || p.equalsIgnoreCase("Severe") || p.equalsIgnoreCase("Extreme") || p.equalsIgnoreCase("High")) {
                task.setPriority("High");
            } else if (p.equalsIgnoreCase("Moderate") || p.equalsIgnoreCase("Medium")) {
                task.setPriority("Medium");
            } else if (p.equalsIgnoreCase("Minor") || p.equalsIgnoreCase("Low")) {
                task.setPriority("Low");
            } else {
                task.setPriority("Medium");
            }
        }
        task.setCreatedAt(LocalDateTime.now());
        if (task.getStatus() == null || task.getStatus().trim().isEmpty()) {
            task.setStatus("Pending");
        }

        RescueTask saved = rescueTaskRepository.save(task);

        // If linked to a citizen report, acknowledge the report
        if (task.getCitizenReportId() != null) {
            try {
                Optional<CitizenReport> reportOpt = citizenReportRepository.findById(task.getCitizenReportId());
                if (reportOpt.isPresent()) {
                    CitizenReport report = reportOpt.get();
                    if ("Pending".equalsIgnoreCase(report.getStatus())) {
                        report.setStatus("Acknowledged");
                        citizenReportRepository.save(report);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to sync citizen report on task creation: {}", e.getMessage());
            }
        }

        return saved;
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
        return rescueTaskRepository.findByAssignedTo(email.trim().toLowerCase());
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

    // Acknowledge task explicitly by responder
    @Transactional
    public RescueTask acknowledgeTask(Long taskId, String responderEmail) {
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        task.setStatus("Acknowledged");
        task.setAcknowledgedAt(LocalDateTime.now());
        task.setAcknowledgedBy(responderEmail);
        RescueTask saved = rescueTaskRepository.save(task);

        if (saved.getCitizenReportId() != null) {
            try {
                citizenReportRepository.findById(saved.getCitizenReportId()).ifPresent(r -> {
                    if ("Pending".equalsIgnoreCase(r.getStatus()) || "Assigned".equalsIgnoreCase(r.getStatus())) {
                        r.setStatus("Acknowledged");
                        citizenReportRepository.save(r);
                        log.info("Cross-role synchronization: Citizen Report #{} marked as Acknowledged", r.getReportId());
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to sync citizen report on task acknowledgement: {}", e.getMessage());
            }
        }
        return saved;
    }

    // Resolve task explicitly with completion details
    @Transactional
    public RescueTask resolveTask(Long taskId, String summary, String responderEmail) {
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        task.setStatus("Completed");
        task.setCompletedAt(LocalDateTime.now());
        if (summary != null && !summary.trim().isEmpty()) {
            task.setCompletionSummary(summary.trim());
            String existingNotes = task.getNotes() != null ? task.getNotes() : "";
            task.setNotes(existingNotes + "\n[RESOLVED " + LocalDateTime.now() + " by " + responderEmail + "]: " + summary.trim());
        }

        RescueTask saved = rescueTaskRepository.save(task);

        // Cross-role synchronization: update linked citizen report to Resolved
        if (saved.getCitizenReportId() != null) {
            try {
                Optional<CitizenReport> reportOpt = citizenReportRepository.findById(saved.getCitizenReportId());
                if (reportOpt.isPresent()) {
                    CitizenReport report = reportOpt.get();
                    report.setStatus("Resolved");
                    citizenReportRepository.save(report);
                    log.info("Cross-role synchronization: Citizen Report #{} marked as Resolved following completion of Rescue Task #{}",
                        saved.getCitizenReportId(), saved.getTaskId());
                }
            } catch (Exception e) {
                log.warn("Failed to update linked citizen report on task resolution: {}", e.getMessage());
            }
        }

        return saved;
    }

    // Validate 5-Stage Mission Workflow transitions
    public boolean isAllowedTransition(String currentStatus, String targetStatus, boolean isAdmin) {
        if (isAdmin) return true; // Central Command admin can override or reassign

        if (currentStatus == null) currentStatus = "Pending";
        currentStatus = currentStatus.trim();
        targetStatus = targetStatus.trim();

        // Same status allowed (e.g. adding a SitRep progress note without changing status)
        if (currentStatus.equalsIgnoreCase(targetStatus)) return true;

        // Terminal state: completed task cannot be altered by responder
        if ("Completed".equalsIgnoreCase(currentStatus) || "Resolved".equalsIgnoreCase(currentStatus)) {
            return false;
        }

        switch (currentStatus.toLowerCase()) {
            case "pending":
            case "assigned":
                return targetStatus.equalsIgnoreCase("Acknowledged") 
                    || targetStatus.equalsIgnoreCase("Accepted") 
                    || targetStatus.equalsIgnoreCase("Rejected");
            case "acknowledged":
            case "accepted":
                return targetStatus.equalsIgnoreCase("En Route")
                    || targetStatus.equalsIgnoreCase("On the Way");
            case "en route":
            case "on the way":
                return targetStatus.equalsIgnoreCase("Arrived") 
                    || targetStatus.equalsIgnoreCase("Reached Location")
                    || targetStatus.equalsIgnoreCase("In Progress")
                    || targetStatus.equalsIgnoreCase("Assistance in Progress");
            case "arrived":
            case "reached location":
            case "in progress":
            case "assistance in progress":
                return targetStatus.equalsIgnoreCase("Completed") 
                    || targetStatus.equalsIgnoreCase("Resolved");
            default:
                return true;
        }
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

        String[] validStatuses = {
            "Assigned", "Acknowledged", "En Route", "On the Way", "Arrived", "Reached Location",
            "Completed", "Resolved", "Pending", "In Progress", "Assistance in Progress", "Accepted", "Rejected"
        };
        boolean isValid = false;
        for (String validStatus : validStatuses) {
            if (validStatus.equalsIgnoreCase(status)) {
                isValid = true;
                break;
            }
        }
        if (!isValid) {
            throw new IllegalArgumentException("Invalid status value. Must be: Pending, Assigned, Acknowledged, Accepted, En Route, On the Way, Arrived, Reached Location, In Progress, Assistance in Progress, Completed, or Resolved");
        }
        
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        task.setStatus(status);

        if (("Acknowledged".equalsIgnoreCase(status) || "Accepted".equalsIgnoreCase(status)) && task.getAcknowledgedAt() == null) {
            task.setAcknowledgedAt(LocalDateTime.now());
            if (task.getCitizenReportId() != null) {
                try {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                        r.setStatus("Acknowledged");
                        r.setProgressStage(status);
                        if (r.getAcknowledgedAt() == null) {
                            r.setAcknowledgedAt(LocalDateTime.now());
                        }
                        citizenReportRepository.save(r);
                    });
                } catch (Exception e) {
                    log.warn("Could not sync citizen report on Acknowledged/Accepted: {}", e.getMessage());
                }
            }
        }

        if (("En Route".equalsIgnoreCase(status) || "On the Way".equalsIgnoreCase(status)) && task.getEnRouteAt() == null) {
            task.setEnRouteAt(LocalDateTime.now());
            if (task.getCitizenReportId() != null) {
                try {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                        r.setStatus("In Progress");
                        r.setProgressStage(status);
                        citizenReportRepository.save(r);
                    });
                } catch (Exception e) {
                    log.warn("Could not sync citizen report on En Route: {}", e.getMessage());
                }
            }
        }

        if (("Arrived".equalsIgnoreCase(status) || "Reached Location".equalsIgnoreCase(status)) && task.getArrivedAt() == null) {
            task.setArrivedAt(LocalDateTime.now());
            if (task.getCitizenReportId() != null) {
                try {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                        r.setStatus("In Progress");
                        r.setProgressStage(status);
                        citizenReportRepository.save(r);
                    });
                } catch (Exception e) {
                    log.warn("Could not sync citizen report on Arrived: {}", e.getMessage());
                }
            }
        }

        if ("In Progress".equalsIgnoreCase(status) || "Assistance in Progress".equalsIgnoreCase(status)) {
            if (task.getCitizenReportId() != null) {
                try {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                        r.setStatus("In Progress");
                        r.setProgressStage(status);
                        citizenReportRepository.save(r);
                    });
                } catch (Exception e) {
                    log.warn("Could not sync citizen report on In Progress: {}", e.getMessage());
                }
            }
        }

        if (("Completed".equalsIgnoreCase(status) || "Resolved".equalsIgnoreCase(status)) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
            if (notes != null && !notes.trim().isEmpty()) {
                task.setCompletionSummary(notes.trim());
            }
            // Sync linked citizen report to Resolved
            if (task.getCitizenReportId() != null) {
                try {
                    citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                        r.setStatus("Resolved");
                        r.setProgressStage(status);
                        if (r.getResolvedAt() == null) {
                            r.setResolvedAt(LocalDateTime.now());
                        }
                        citizenReportRepository.save(r);
                        log.info("Cross-role synchronization: Citizen Report #{} marked as Resolved following completion of Rescue Task #{}",
                            r.getReportId(), taskId);
                    });
                } catch (Exception e) {
                    log.warn("Could not sync citizen report on task completion: {}", e.getMessage());
                }
            }
        }

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
        
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        task.setAssignedTo(responderEmail.trim().toLowerCase());
        task.setAssignedResponderName(responderName.trim());
        if ("Pending".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("Assigned");
        }
        RescueTask savedTask = rescueTaskRepository.save(task);

        if (task.getCitizenReportId() != null) {
            try {
                citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(r -> {
                    r.setAssignedResponderEmail(responderEmail.trim().toLowerCase());
                    r.setAssignedResponderName(responderName.trim());
                    userRepository.findByEmail(responderEmail.trim().toLowerCase()).ifPresent(u -> {
                        if (u.getPhone() != null && !u.getPhone().isEmpty()) {
                            r.setAssignedResponderPhone(u.getPhone());
                        }
                    });
                    r.setStatus("Assigned");
                    r.setProgressStage("Assigned");
                    r.setRescueTaskId(task.getTaskId());
                    if (r.getAssignedAt() == null) {
                        r.setAssignedAt(LocalDateTime.now());
                    }
                    citizenReportRepository.save(r);
                    log.info("Synchronized CitizenReport #{} with assigned responder {}", r.getReportId(), responderEmail);
                });
            } catch (Exception e) {
                log.warn("Could not sync citizen report on task assignment: {}", e.getMessage());
            }
        }

        return savedTask;
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
        return rescueTaskRepository.findByAssignedToAndStatus(email.trim().toLowerCase(), status);
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

    // Responder updates SitRep notes on their assigned task
    @Transactional
    public RescueTask updateTaskNotes(Long taskId, String notes, String callerEmail, boolean isAdmin) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        if (!isAdmin) {
            String assignedTo = task.getAssignedTo();
            if (assignedTo == null || !assignedTo.trim().equalsIgnoreCase(callerEmail.trim())) {
                throw new SecurityException("Forbidden: You are not authorized to edit notes for task #" + taskId + " assigned to another unit.");
            }
        }

        if (notes != null && !notes.trim().isEmpty()) {
            String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String noteEntry = "[" + timestamp + " - " + callerEmail + "] " + notes.trim();
            String existingNotes = task.getNotes();
            if (existingNotes == null || existingNotes.trim().isEmpty()) {
                task.setNotes(noteEntry);
            } else {
                task.setNotes(existingNotes + "\n" + noteEntry);
            }
        }

        return rescueTaskRepository.save(task);
    }

    // Responder or Admin requests cancellation / reassignment of task
    @Transactional
    public RescueTask requestCancellation(Long taskId, String reason, String callerEmail, boolean isAdmin) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID cannot be null");
        }
        RescueTask task = rescueTaskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        if (!isAdmin) {
            String assignedTo = task.getAssignedTo();
            if (assignedTo == null || !assignedTo.trim().equalsIgnoreCase(callerEmail.trim())) {
                throw new SecurityException("Forbidden: You are not authorized to request cancellation for task #" + taskId + " assigned to another unit.");
            }
        }

        task.setCancellationRequested(true);
        task.setCancellationReason(reason != null ? reason.trim() : "Responder requested cancellation / reassignment.");
        task.setStatus("Cancellation Requested");

        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String cancellationLog = "[" + timestamp + " CANCEL_REQUESTED by " + callerEmail + "] " + (reason != null ? reason : "No reason specified");
        String existingNotes = task.getNotes();
        task.setNotes(existingNotes == null ? cancellationLog : existingNotes + "\n" + cancellationLog);

        RescueTask savedTask = rescueTaskRepository.save(task);

        // Synchronize linked citizen report if applicable
        if (task.getCitizenReportId() != null) {
            try {
                citizenReportRepository.findById(task.getCitizenReportId()).ifPresent(report -> {
                    report.setCancellationRequested(true);
                    report.setCancellationReason("Responder " + callerEmail + " requested reassignment: " + (reason != null ? reason : ""));
                    report.setStatus("Cancellation Requested");
                    citizenReportRepository.save(report);
                    log.info("Synchronized CitizenReport #{} status to Cancellation Requested", report.getReportId());
                });
            } catch (Exception e) {
                log.warn("Failed to synchronize citizen report cancellation: {}", e.getMessage());
            }
        }

        return savedTask;
    }
}
