-- ==============================================================================
-- DISASTER MANAGEMENT & ALERT SYSTEM
-- MEDIA STORAGE, LIVE TRACKING & COLLABORATION ROOM SCHEMA MIGRATION
-- Compatible with MySQL 8.0+ / Spring Data JPA
-- ==============================================================================

USE disaster_management;

-- 1. Incident Media Table (Stores metadata and safe disk path; avoids DB binary bloat)
CREATE TABLE IF NOT EXISTS incident_media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NULL,
    incident_type VARCHAR(50) NULL DEFAULT 'CITIZEN_REPORT',
    uploader_email VARCHAR(255) NOT NULL,
    uploader_name VARCHAR(255) NULL,
    uploader_role VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL, -- PHOTO, VIDEO, AUDIO, DOCUMENT
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    caption TEXT NULL,
    capture_latitude DOUBLE NULL,
    capture_longitude DOUBLE NULL,
    capture_accuracy DOUBLE NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_media_incident (incident_id, incident_type),
    INDEX idx_media_uploader (uploader_email),
    INDEX idx_media_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Live Tracking Sessions Table (Delivery-app style tracking session per assignment)
CREATE TABLE IF NOT EXISTS tracking_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    responder_email VARCHAR(255) NOT NULL,
    responder_name VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED, DISCONNECTED
    destination_latitude DOUBLE NULL,
    destination_longitude DOUBLE NULL,
    destination_address VARCHAR(255) NULL,
    current_latitude DOUBLE NULL,
    current_longitude DOUBLE NULL,
    current_accuracy DOUBLE NULL,
    current_heading DOUBLE NULL,
    current_speed DOUBLE NULL,
    estimated_distance_meters DOUBLE NULL,
    estimated_duration_seconds INT NULL,
    last_heartbeat_at DATETIME(6) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    INDEX idx_tracking_assignment (assignment_id),
    INDEX idx_tracking_responder (responder_email),
    INDEX idx_tracking_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Responder Location Breadcrumb History Table (High-resolution telemetry trail)
CREATE TABLE IF NOT EXISTS responder_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    responder_email VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    accuracy DOUBLE NULL,
    heading DOUBLE NULL,
    speed DOUBLE NULL,
    recorded_at DATETIME(6) NOT NULL,
    INDEX idx_loc_session (session_id),
    INDEX idx_loc_assignment (assignment_id),
    INDEX idx_loc_responder_recorded (responder_email, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Incident Collaboration Room Timeline Events (Shared chronology & communication)
CREATE TABLE IF NOT EXISTS incident_timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    incident_type VARCHAR(50) NOT NULL DEFAULT 'CITIZEN_REPORT',
    event_type VARCHAR(50) NOT NULL, -- REPORTED, VERIFIED, ASSIGNED, EN_ROUTE, ON_SCENE, RESOLVED, MEDIA_UPLOADED, MESSAGE
    actor_email VARCHAR(255) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    actor_name VARCHAR(255) NULL,
    message TEXT NOT NULL,
    media_id BIGINT NULL,
    payload_json TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_timeline_incident (incident_id, incident_type),
    INDEX idx_timeline_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

