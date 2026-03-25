package com.disaster.management.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtUtil;
import com.disaster.management.service.RescueTaskService;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = {"http://localhost:8080", "http://localhost:8081"})
public class RescueTaskController {

    @Autowired
    private RescueTaskService rescueTaskService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;

    // Create a new task (Admin only)
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody RescueTask task, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        // Validate token and get email
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        
        // Verify admin role
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent() || !"Admin".equals(userOpt.get().getRole())) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            task.setCreatedBy(email);
            RescueTask createdTask = rescueTaskService.createTask(task);
            
            response.put("status", "success");
            response.put("message", "Task created successfully");
            response.put("task", createdTask);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to create task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all tasks (Admin)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTasks(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);
        
        List<RescueTask> tasks;
        if ("Admin".equals(role)) {
            tasks = rescueTaskService.getAllTasksSorted();
        } else {
            // Responders only see their assigned tasks
            tasks = rescueTaskService.getTasksByResponder(email);
        }
        
        response.put("status", "success");
        response.put("tasks", tasks);
        return ResponseEntity.ok(response);
    }

    // Get tasks for logged-in responder
    @GetMapping("/my-tasks")
    public ResponseEntity<Map<String, Object>> getMyTasks(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        List<RescueTask> tasks = rescueTaskService.getTasksByResponder(email);
        
        response.put("status", "success");
        response.put("tasks", tasks);
        return ResponseEntity.ok(response);
    }

    // Get task by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTaskById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
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

    // Assign task to responder (Admin only)
    @PutMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignTask(@PathVariable Long id, @RequestParam String responderEmail, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);
        
        if (!"Admin".equals(role)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        // Verify responder exists
        Optional<User> responderOpt = userRepository.findByEmail(responderEmail);
        if (!responderOpt.isPresent() || !"Responder".equals(responderOpt.get().getRole())) {
            response.put("status", "error");
            response.put("message", "Responder not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        try {
            String responderName = responderOpt.get().getName();
            RescueTask task = rescueTaskService.assignTask(id, responderEmail, responderName);
            
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
            response.put("status", "error");
            response.put("message", "Failed to assign task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Update task status (Responder or Admin)
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateTaskStatus(@PathVariable Long id, @RequestBody Map<String, String> request, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String status = request.get("status");
        String notes = request.get("notes");
        
        if (status == null || status.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Status is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            RescueTask task = rescueTaskService.updateTaskStatus(id, status, notes);
            
            if (task != null) {
                response.put("status", "success");
                response.put("message", "Task status updated");
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
            response.put("status", "error");
            response.put("message", "Failed to update task status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Delete task (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String role = jwtUtil.extractRole(token);
        if (!"Admin".equals(role)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        try {
            rescueTaskService.deleteTask(id);
            response.put("status", "success");
            response.put("message", "Task deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to delete task: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all responders (Admin only)
    @GetMapping("/responders")
    public ResponseEntity<Map<String, Object>> getAllResponders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.put("status", "error");
            response.put("message", "Invalid or expired token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        String role = jwtUtil.extractRole(token);
        if (!"Admin".equals(role)) {
            response.put("status", "error");
            response.put("message", "Access denied. Admin only.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        List<User> responders = userRepository.findAll().stream()
                .filter(u -> "Responder".equals(u.getRole()))
                .toList();
        
        response.put("status", "success");
        response.put("responders", responders);
        return ResponseEntity.ok(response);
    }
}
