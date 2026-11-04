package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.AdminPinService;
import com.disaster.management.service.IncidentTimelineService;
import com.disaster.management.service.RescueTaskService;
import com.disaster.management.service.ResponderTrackingService;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081", "http://127.0.0.1:8080", "http://127.0.0.1:8081"})
public class RescueTaskController {

    private static final Logger log = LoggerFactory.getLogger(RescueTaskController.class);

    @Autowired
    private RescueTaskService rescueTaskService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminPinService adminPinService;

    @Autowired
    private ResponderTrackingService responderTrackingService;

    @Autowired
    private IncidentTimelineService incidentTimelineService;

    // Create a new task (Admin only)
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody RescueTask task) {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : "admin@disaster.gov.in";

        try {
            task.setCreatedBy(email);
            RescueTask createdTask = rescueTaskService.createTask(task);

            response.put("status", "success");
            response.put("message", "Rescue task created successfully");
            response.put("task", createdTask);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Failed to create task: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to create rescue task");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all tasks (Admin sees all; Responders see their assigned tasks)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTasks() {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        List<RescueTask> tasks;
        if (isAdmin) {
            tasks = rescueTaskService.getAllTasksSorted();
        } else {
            tasks = rescueTaskService.getTasksByResponder(email);
        }

        response.put("status", "success");
        response.put("tasks", tasks);
        return ResponseEntity.ok(response);
    }

    // Get tasks for logged-in responder
    @GetMapping({"/my-tasks", "/my"})
    public ResponseEntity<Map<String, Object>> getMyTasks() {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<RescueTask> tasks = rescueTaskService.getTasksByResponder(auth.getName());
        response.put("status", "success");
        response.put("tasks", tasks);
        return ResponseEntity.ok(response);
    }

    // Get task by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTaskById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        Optional<RescueTask> task = rescueTaskService.getTaskById(id);
        if (task.isPresent()) {
            response.put("status", "success");
            response.put("task", task.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Task not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // Assign task to responder (Admin only) - supports both PUT and POST
    @PutMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTask(
            @PathVariable Long id,
            @RequestParam String responderEmail) {
        return executeAssignTask(id, responderEmail);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTaskPost(
            @PathVariable Long id,
            @RequestParam(required = false) String responderEmail,
            @RequestBody(required = false) Map<String, String> body) {
        String email = responderEmail;
        if (email == null && body != null) {
            email = body.get("responderEmail") != null ? body.get("responderEmail") : body.get("email");
        }
        return executeAssignTask(id, email);
    }

    private ResponseEntity<Map<String, Object>> executeAssignTask(Long id, String responderEmail) {
        Map<String, Object> response = new HashMap<>();

        if (responderEmail == null || responderEmail.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Responder email is required for assignment");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> responderOpt = userRepository.findByEmail(responderEmail.trim().toLowerCase());
        if (responderOpt.isEmpty() || !"Responder".equals(responderOpt.get().getRole())) {
            response.put("status", "error");
            response.put("message", "Valid responder not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User responderUser = responderOpt.get();
        String avail = responderUser.getAvailabilityStatus();
        if (avail != null && (avail.equalsIgnoreCase("Unavailable") || avail.equalsIgnoreCase("Offline"))) {
            response.put("status", "error");
            response.put("message", "Cannot assign task: Responder " + responderUser.getName() + " is currently " + avail);
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String responderName = responderUser.getName();
            RescueTask task = rescueTaskService.assignTask(id, responderEmail.trim().toLowerCase(), responderName);

            // Automatically synchronize responder state to On Duty
            if (avail == null || avail.equalsIgnoreCase("Available")) {
                responderUser.setAvailabilityStatus("On Duty");
                userRepository.save(responderUser);
            }

            // Record administrative audit trail
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth != null ? auth.getName() : "admin@disaster.gov.in";
            adminPinService.recordAudit(
                adminEmail,
                "ASSIGN_RESCUE_TASK",
                "RescueTask",
                String.valueOf(id),
                "SUCCESS",
                "Assigned to " + responderName + " (" + responderEmail + ")",
                null
            );

            if (task != null) {
                response.put("status", "success");
                response.put("message", "Task assigned successfully");
                response.put("task", task);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Task not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Failed to assign task: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to assign task");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Acknowledge task (Responder assigned or Admin) - supports both POST and PUT
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeTaskPost(@PathVariable Long id) {
        return executeAcknowledgeTask(id);
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeTaskPut(@PathVariable Long id) {
        return executeAcknowledgeTask(id);
    }

    private ResponseEntity<Map<String, Object>> executeAcknowledgeTask(Long id) {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        Optional<RescueTask> taskOpt = rescueTaskService.getTaskById(id);
        if (taskOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Task not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        RescueTask existing = taskOpt.get();
        if (!isAdmin) {
            String assignedTo = existing.getAssignedTo();
            if (assignedTo == null || !assignedTo.trim().equalsIgnoreCase(callerEmail.trim())) {
                response.put("status", "error");
                response.put("message", "Forbidden: You are not authorized to acknowledge assignment #" + id + " assigned to another responder.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

        try {
            RescueTask acknowledgedTask = rescueTaskService.acknowledgeTask(id, callerEmail);
            try {
                incidentTimelineService.logEvent(
                    id, "RESCUE_TASK", "TASK_ACKNOWLEDGED", callerEmail,
                    isAdmin ? "Admin" : "Responder",
                    existing.getAssignedResponderName() != null ? existing.getAssignedResponderName() : callerEmail,
                    "Task acknowledged by " + (existing.getAssignedResponderName() != null ? existing.getAssignedResponderName() : callerEmail),
                    null, null
                );
            } catch (Exception e) {
                log.warn("Timeline log warning: {}", e.getMessage());
            }

            response.put("status", "success");
            response.put("message", "Task acknowledged successfully");
            response.put("task", acknowledgedTask);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to acknowledge task {}: {}", id, e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to acknowledge task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Resolve task (Responder assigned or Admin) - supports both POST and PUT
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveTaskPost(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String summary = body != null ? (body.get("summary") != null ? body.get("summary") : body.get("notes")) : "Task marked as completed.";
        return executeResolveTask(id, summary);
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveTaskPut(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String summary = body != null ? (body.get("summary") != null ? body.get("summary") : body.get("notes")) : "Task marked as completed.";
        return executeResolveTask(id, summary);
    }

    private ResponseEntity<Map<String, Object>> executeResolveTask(Long id, String summary) {
        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        Optional<RescueTask> taskOpt = rescueTaskService.getTaskById(id);
        if (taskOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Task not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        RescueTask existing = taskOpt.get();
        if (!isAdmin) {
            String assignedTo = existing.getAssignedTo();
            if (assignedTo == null || !assignedTo.trim().equalsIgnoreCase(callerEmail.trim())) {
                response.put("status", "error");
                response.put("message", "Forbidden: You are not authorized to resolve assignment #" + id + " assigned to another responder.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

        try {
            RescueTask resolvedTask = rescueTaskService.resolveTask(id, summary, callerEmail);

            // Terminate any active GPS tracking session
            try {
                responderTrackingService.stopTracking(id, callerEmail);
            } catch (Exception e) {
                log.warn("Tracking stop on task resolution: {}", e.getMessage());
            }

            // Timeline log
            try {
                incidentTimelineService.logEvent(
                    id, "RESCUE_TASK", "TASK_RESOLVED", callerEmail,
                    isAdmin ? "Admin" : "Responder",
                    existing.getAssignedResponderName() != null ? existing.getAssignedResponderName() : callerEmail,
                    "Mission completed: " + (summary != null ? summary : "Resolved successfully"),
                    null, null
                );
            } catch (Exception e) {
                log.warn("Timeline log warning: {}", e.getMessage());
            }

            response.put("status", "success");
            response.put("message", "Task marked as Completed / Resolved.");
            response.put("task", resolvedTask);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to resolve task {}: {}", id, e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to resolve task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Update task status (Responder or Admin)
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateTaskStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            response.put("status", "error");
            response.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String callerEmail = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_Admin"));

        Optional<RescueTask> taskOpt = rescueTaskService.getTaskById(id);
        if (taskOpt.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Task not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        RescueTask existingTask = taskOpt.get();

        // Strict ownership check: Responders can only modify tasks assigned to them!
        if (!isAdmin) {
            String assignedTo = existingTask.getAssignedTo();
            if (assignedTo == null || !assignedTo.trim().equalsIgnoreCase(callerEmail.trim())) {
                response.put("status", "error");
                response.put("message", "Forbidden: You are not authorized to update assignment #" + id + " assigned to " + (assignedTo != null ? assignedTo : "another unit"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

        String currentStatus = existingTask.getStatus() != null ? existingTask.getStatus().trim() : "Pending";
        String targetStatus = request.get("status");
        String notes = request.get("notes");

        if (targetStatus == null || targetStatus.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Status is required");
            return ResponseEntity.badRequest().body(response);
        }

        targetStatus = targetStatus.trim();

        // Prevent invalid transitions from completed/resolved terminal states
        if (("Completed".equalsIgnoreCase(currentStatus) || "Resolved".equalsIgnoreCase(currentStatus)) && !isAdmin) {
            if ("Completed".equalsIgnoreCase(targetStatus) || "Resolved".equalsIgnoreCase(targetStatus)) {
                response.put("status", "success");
                response.put("message", "Task is already resolved.");
                response.put("task", existingTask);
                return ResponseEntity.ok(response);
            }
            response.put("status", "error");
            response.put("message", "Invalid transition: A completed rescue mission cannot be reopened to '" + targetStatus + "'. Contact Central Command.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate 5-Stage Mission Workflow transitions
        if (!rescueTaskService.isAllowedTransition(currentStatus, targetStatus, isAdmin)) {
            response.put("status", "error");
            response.put("message", "Invalid mission transition: Cannot change status from '" + currentStatus + "' to '" + targetStatus + "'. Missions must follow the lifecycle: ASSIGNED -> ACKNOWLEDGED -> EN ROUTE -> ARRIVED -> COMPLETED.");
            return ResponseEntity.badRequest().body(response);
        }

        // Audited action if Admin reopens completed task
        if (("Completed".equalsIgnoreCase(currentStatus) || "Resolved".equalsIgnoreCase(currentStatus)) && isAdmin) {
            adminPinService.recordAudit(
                callerEmail,
                "REOPEN_RESCUE_TASK",
                "RescueTask",
                String.valueOf(id),
                "SUCCESS",
                "Administrative override: Reopened completed task #" + id + " to " + targetStatus,
                null
            );
        }

        try {
            RescueTask task = rescueTaskService.updateTaskStatus(id, targetStatus, notes);

            // If target status is Completed or Resolved, terminate any active tracking session
            if ("Completed".equalsIgnoreCase(targetStatus) || "Resolved".equalsIgnoreCase(targetStatus)) {
                try {
                    responderTrackingService.stopTracking(id, callerEmail);
                } catch (Exception e) {
                    log.warn("Tracking stop on task completion: {}", e.getMessage());
                }
            }

            // Timeline event logging
            try {
                incidentTimelineService.logEvent(
                    id,
                    "RESCUE_TASK",
                    targetStatus.toUpperCase().replace(" ", "_"),
                    callerEmail,
                    isAdmin ? "Admin" : "Responder",
                    existingTask.getAssignedResponderName() != null ? existingTask.getAssignedResponderName() : callerEmail,
                    "Task status updated to " + targetStatus + (notes != null && !notes.isEmpty() ? ": " + notes : ""),
                    null,
                    null
                );
            } catch (Exception e) {
                log.warn("Timeline log error: {}", e.getMessage());
            }

            response.put("status", "success");
            response.put("message", "Task status updated to " + targetStatus);
            response.put("task", task);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Failed to update task status: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to update task status");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Delete task (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            rescueTaskService.deleteTask(id);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth != null ? auth.getName() : "admin@disaster.gov.in";
            adminPinService.recordAudit(
                adminEmail,
                "DELETE_RESCUE_TASK",
                "RescueTask",
                String.valueOf(id),
                "SUCCESS",
                "Permanently deleted rescue task #" + id,
                null
            );

            response.put("status", "success");
            response.put("message", "Task deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Failed to delete task: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to delete task");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all responders (Admin only)
    @GetMapping("/responders")
    public ResponseEntity<Map<String, Object>> getAllResponders() {
        Map<String, Object> response = new HashMap<>();
        List<User> responders = userRepository.findByRole("Responder");
        response.put("status", "success");
        response.put("responders", responders);
        return ResponseEntity.ok(response);
    }
}
