package com.disaster.management.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup Readiness Validator
 * Validates that Spring Boot, MySQL connectivity, required database tables,
 * storage directories, and essential services are fully operational before
 * declaring the server as READY.
 *
 * If any critical requirement fails, logs an explicit diagnostic error banner
 * and triggers immediate graceful shutdown to avoid operating on corrupted or incomplete state.
 */
@Component
public class StartupReadinessValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupReadinessValidator.class);

    private static final List<String> MANDATORY_TABLES = List.of(
            "users",
            "citizen_reports",
            "alerts",
            "messages",
            "rescue_tasks",
            "tracking_sessions",
            "responder_locations",
            "incident_media",
            "official_alerts"
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ServerRuntimeStateHolder stateHolder;

    @Autowired
    private ServerLifecycleManager serverLifecycleManager;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.media.upload-dir:uploads/incident-media}")
    private String mediaUploadDir;

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        log.info("Executing comprehensive startup readiness validation...");

        // 1. Database Connection and Schema Integrity Validation
        List<String> missingTables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(3)) {
                handleStartupFailure("MySQL connection failed validation check (isValid returned false).");
                return;
            }

            DatabaseMetaData metaData = conn.getMetaData();
            for (String tableName : MANDATORY_TABLES) {
                try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                    if (!rs.next()) {
                        missingTables.add(tableName);
                    }
                }
            }
        } catch (Exception e) {
            handleStartupFailure("Unable to connect to MySQL database: " + e.getMessage());
            return;
        }

        if (!missingTables.isEmpty()) {
            handleStartupFailure("Critical database tables are missing: " + String.join(", ", missingTables));
            return;
        }

        // 2. Media Upload Storage Validation
        File uploadFolder = new File(mediaUploadDir);
        if (!uploadFolder.exists()) {
            boolean created = uploadFolder.mkdirs();
            if (!created && !uploadFolder.exists()) {
                handleStartupFailure("Failed to initialize media storage directory: " + uploadFolder.getAbsolutePath());
                return;
            }
        }

        if (!uploadFolder.canWrite()) {
            handleStartupFailure("Media storage directory is not writable: " + uploadFolder.getAbsolutePath());
            return;
        }

        // 3. Mark Server State as READY
        stateHolder.setStatus(ServerRuntimeStatus.READY);

        // 4. Print Operational Readiness Banner
        printStartupSuccessBanner();
    }

    private void handleStartupFailure(String reason) {
        stateHolder.setFailed(reason);
        System.err.println("\n" + "=".repeat(85));
        System.err.println(" [FATAL STARTUP ERROR] SERVER READINESS VALIDATION FAILED");
        System.err.println("=".repeat(85));
        System.err.println(" CAUSE: " + reason);
        System.err.println(" ACTION: Shutting down partially initialized server to prevent operating on incomplete data.");
        System.err.println(" PORT: Releasing port " + serverPort + " and terminating gracefully.");
        System.err.println("=".repeat(85) + "\n");

        serverLifecycleManager.initiateGracefulShutdown("Startup failure: " + reason);
    }

    private void printStartupSuccessBanner() {
        System.out.println("\n" + "=".repeat(85));
        System.out.println(" [SERVER READY] Integrated Disaster Reporting & Emergency Response Coordination System");
        System.out.println("=".repeat(85));
        System.out.println(" Status:          READY (All critical requirements validated)");
        System.out.println(" Active Port:     " + serverPort + " (HTTP)");
        System.out.println(" Database:        MySQL 8.0 (disaster_management) - All " + MANDATORY_TABLES.size() + " tables verified");
        System.out.println(" Media Storage:   " + mediaUploadDir + " (Active, writable)");
        System.out.println(" Portals Active:");
        System.out.println("   - Admin Command Center:    http://localhost:" + serverPort + "/admin-dashboard.html");
        System.out.println("   - Citizen Reporting:       http://localhost:" + serverPort + "/citizen-dashboard.html");
        System.out.println("   - Field Responder HUD:     http://localhost:" + serverPort + "/responder-dashboard.html");
        System.out.println(" Health Actuator:             http://localhost:" + serverPort + "/actuator/health");
        System.out.println("=".repeat(85) + "\n");
    }
}
