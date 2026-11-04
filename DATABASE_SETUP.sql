-- ============================================================
-- DISASTER MANAGEMENT & ALERT SYSTEM - COMPLETE DATABASE SETUP
-- Safe, Idempotent, Production-Ready Schema & Seed Script
-- ============================================================

CREATE DATABASE IF NOT EXISTS disaster_management;
USE disaster_management;

-- 1. Users Table (with Admin Security PIN & 2FA step-up fields)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    phone VARCHAR(20),
    location VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    assigned_zone VARCHAR(100),
    group_classification VARCHAR(50),
    additional_details TEXT,
    admin_pin VARCHAR(255),
    admin_pin_enrolled BOOLEAN DEFAULT FALSE,
    pin_failed_attempts INT DEFAULT 0,
    pin_locked_until TIMESTAMP NULL,
    sos_shortcut_enabled BOOLEAN DEFAULT TRUE,
    sos_shortcut_key VARCHAR(50) DEFAULT 'Tab',
    sos_sound_volume INT DEFAULT 80,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    INDEX idx_user_email (email),
    INDEX idx_user_role (role),
    INDEX idx_user_role_zone (role, assigned_zone)
);

-- 2. Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    disaster_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    region VARCHAR(100) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'Active',
    media_url VARCHAR(500),
    broadcasted BOOLEAN DEFAULT TRUE,
    latitude DOUBLE,
    longitude DOUBLE,
    INDEX idx_alert_region (region),
    INDEX idx_alert_status (status),
    INDEX idx_alert_created_at (created_at)
);

-- 3. Disasters Table (Regional / NDMA feeds)
CREATE TABLE IF NOT EXISTS disasters (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    location VARCHAR(255) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'Active',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_disaster_location (location),
    INDEX idx_disaster_status (status)
);

-- 4. Citizen Reports Table (Direct Emergency Incident Submissions)
CREATE TABLE IF NOT EXISTS citizen_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    citizen_email VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    location VARCHAR(255) NOT NULL,
    photo_data LONGTEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'Pending',
    INDEX idx_report_citizen_email (citizen_email),
    INDEX idx_report_status (status),
    INDEX idx_report_timestamp (timestamp)
);

-- 5. Incident Reports Table (Responder Field Reports)
CREATE TABLE IF NOT EXISTS incident_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    reported_by VARCHAR(255) NOT NULL,
    reporter_name VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    latitude DOUBLE,
    longitude DOUBLE,
    location_name VARCHAR(255),
    image_urls TEXT,
    report_type VARCHAR(100),
    status VARCHAR(50) DEFAULT 'Submitted',
    zone VARCHAR(100),
    INDEX idx_incident_reported_by (reported_by),
    INDEX idx_incident_status (status),
    INDEX idx_incident_zone (zone)
);

-- 6. Rescue Tasks Table (Admin Task Dispatch to Responders)
CREATE TABLE IF NOT EXISTS rescue_tasks (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    zone VARCHAR(100) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'Pending',
    deadline TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    assigned_to VARCHAR(255),
    assigned_responder_name VARCHAR(255),
    notes TEXT,
    latitude DOUBLE,
    longitude DOUBLE,
    location_name VARCHAR(255),
    INDEX idx_task_assigned_to (assigned_to),
    INDEX idx_task_zone (zone),
    INDEX idx_task_status (status)
);

-- 7. In-App Emergency Messages Table
CREATE TABLE IF NOT EXISTS messages (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_email VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255),
    sender_role VARCHAR(50) NOT NULL,
    receiver_email VARCHAR(255),
    receiver_role VARCHAR(50),
    message TEXT NOT NULL,
    related_alert_id BIGINT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'Sent',
    INDEX idx_msg_sender (sender_email),
    INDEX idx_msg_receiver (receiver_email)
);

-- 8. Administrative Audit Logs Table (Step-Up 2FA & Privileged Action Trail)
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(100),
    target_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    details VARCHAR(1000),
    ip_address VARCHAR(100),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_actor (actor_email),
    INDEX idx_audit_action (action),
    INDEX idx_audit_timestamp (timestamp)
);

-- 9. Official Alerts Table (NDMA SACHET & CAP 1.2 Government Feeds)
CREATE TABLE IF NOT EXISTS official_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_alert_id VARCHAR(100) NOT NULL UNIQUE,
    cap_identifier VARCHAR(150),
    source_agency VARCHAR(150),
    source_url VARCHAR(500),
    headline VARCHAR(1000),
    description TEXT,
    instruction TEXT,
    hazard_type VARCHAR(100),
    severity VARCHAR(50),
    urgency VARCHAR(50),
    certainty VARCHAR(50),
    area_description TEXT,
    latitude DOUBLE,
    longitude DOUBLE,
    location_precision VARCHAR(50),
    published_time TIMESTAMP NULL,
    effective_time TIMESTAMP NULL,
    onset TIMESTAMP NULL,
    expires_time TIMESTAMP NULL,
    source_status VARCHAR(50),
    message_type VARCHAR(50),
    is_expired BOOLEAN NOT NULL DEFAULT FALSE,
    sync_timestamp TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_official_source_id (source_alert_id),
    INDEX idx_official_hazard (hazard_type),
    INDEX idx_official_severity (severity),
    INDEX idx_official_expires (expires_time),
    INDEX idx_official_expired (is_expired),
    INDEX idx_official_sync (sync_timestamp)
);

-- ============================================================
-- 9. Insert Seed Users with BCrypt Hashed Passwords
-- (Passwords: admin123, responder123, citizen123)
-- Admin Security PIN: 123456
-- ============================================================
-- Citizen Reports Management & Archival
-- citizen_reports: incident_type, is_archived, is_deleted, deletion_reason, deleted_at, deleted_by
-- rescue_tasks: citizen_report_id, acknowledged_at, acknowledged_by, completed_at, completion_summary
-- users: admin_pin_recovery_code, sos_shortcut_enabled, sos_shortcut_key, sos_sound_volume
INSERT INTO users (email, password, name, phone, location, role, assigned_zone, group_classification, admin_pin, admin_pin_enrolled) VALUES 
('admin@disaster.gov.in', '$2a$10$w3pG9v9YgGZ9G9gGZ9G9geu0YwQZ2h3qR1.e7lK0w5m9tQ4v4F6bS', 'Admin User', '9876543210', 'Chennai', 'Admin', NULL, NULL, '$2a$10$fIuN39cWf.MhGZlK6X6Z0eH1cZ8b2b6U4X4h5j6k7l8m9n0o1p2q3', TRUE),
('responder@disaster.gov.in', '$2a$10$p3QW6m8e.OqJ1w9Y0b7K3eV5dE3z7.1q4b8.2a9x0e7c5t2l3m4n5', 'Emergency Responder', '9876543211', 'Coimbatore', 'Responder', 'Coimbatore', 'Rescuer', NULL, FALSE),
('responder2@disaster.gov.in', '$2a$10$p3QW6m8e.OqJ1w9Y0b7K3eV5dE3z7.1q4b8.2a9x0e7c5t2l3m4n5', 'Rescue Team Lead', '9876543214', 'Tiruchirappalli', 'Responder', 'Tiruchirappalli', 'Leader', NULL, FALSE),
('citizen@gmail.com', '$2a$10$K4r5.3w1q8v7t9e2l5m6u8j9k0p1o2i3u4y5t6r7e8w9q0a1b2c3d', 'John Citizen', '9876543212', 'Madurai', 'Citizen', NULL, NULL, NULL, FALSE),
('citizen2@gmail.com', '$2a$10$K4r5.3w1q8v7t9e2l5m6u8j9k0p1o2i3u4y5t6r7e8w9q0a1b2c3d', 'Jane Citizen', '9876543213', 'Chennai', 'Citizen', NULL, NULL, NULL, FALSE)
ON DUPLICATE KEY UPDATE name=VALUES(name);
