package com.disaster.management.lifecycle;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemLifecycleController {

    private final ServerLifecycleManager lifecycleManager;
    private final ServerRuntimeStateHolder stateHolder;

    public SystemLifecycleController(ServerLifecycleManager lifecycleManager, ServerRuntimeStateHolder stateHolder) {
        this.lifecycleManager = lifecycleManager;
        this.stateHolder = stateHolder;
    }

    @GetMapping("/health")
    public ResponseEntity<?> getSystemHealth() {
        return ResponseEntity.ok(Map.of(
            "status", stateHolder.getStatus().name(),
            "uptimeSeconds", stateHolder.getUptimeSeconds()
        ));
    }

    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown() {
        new Thread(() -> {
            try { Thread.sleep(400); } catch (Exception ignored) {}
            lifecycleManager.initiateGracefulShutdown("Shutdown requested via /api/system/shutdown");
        }).start();

        return ResponseEntity.ok(Map.of(
            "status", "initiating_shutdown",
            "message", "Disaster Response System server is shutting down gracefully."
        ));
    }
}
