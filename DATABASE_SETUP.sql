-- ============================================
-- DISASTER MANAGEMENT SYSTEM - COMPLETE DATABASE SETUP
-- Execute this file in MySQL Workbench to set up the entire database
-- ============================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS disaster_management;
USE disaster_management;

-- ============================================
-- 2. Create Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    phone VARCHAR(20),
    location VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 3. Create Alerts Table
-- ============================================
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
    INDEX idx_region (region),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

-- ============================================
-- 4. Create Disasters Table
-- ============================================
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
    INDEX idx_location (location),
    INDEX idx_status (status)
);

-- ============================================
-- 5. Clear Existing Data (Optional - for fresh start)
-- ============================================
-- Uncomment the following lines if you want to start fresh:
-- DELETE FROM alerts;
-- DELETE FROM disasters;
-- DELETE FROM users;

-- ============================================
-- 6. Insert Sample Users
-- ============================================
INSERT INTO users (email, password, name, phone, location, role) VALUES 
('admin@disaster.gov.in', 'admin123', 'Admin User', '9876543210', 'Chennai', 'Admin'),
('responder@disaster.gov.in', 'responder123', 'Emergency Responder', '9876543211', 'Coimbatore', 'Responder'),
('citizen@gmail.com', 'citizen123', 'John Citizen', '9876543212', 'Madurai', 'Citizen'),
('citizen2@gmail.com', 'citizen123', 'Jane Citizen', '9876543213', 'Chennai', 'Citizen'),
('responder2@disaster.gov.in', 'responder123', 'Rescue Team Lead', '9876543214', 'Tiruchirappalli', 'Responder')
ON DUPLICATE KEY UPDATE email=email;

-- ============================================
-- 7. Insert Sample Alerts
-- ============================================
INSERT INTO alerts (title, message, disaster_type, severity, region, created_by, status) VALUES
('Heavy Rainfall Warning', 'Heavy rainfall expected in the next 24 hours. Stay indoors and avoid unnecessary travel. Keep emergency supplies ready.', 'Heavy Rain', 'High', 'Chennai', 'admin@disaster.gov.in', 'Active'),
('Cyclone Alert', 'Severe cyclone approaching coastal areas. Evacuate low-lying regions immediately. Move to designated shelters.', 'Cyclone', 'High', 'Tamil Nadu Coast', 'admin@disaster.gov.in', 'Active'),
('Flood Warning', 'River water levels rising rapidly. Residents near riverbanks should move to higher ground immediately.', 'Flood', 'Medium', 'Coimbatore', 'admin@disaster.gov.in', 'Active'),
('Dense Fog Advisory', 'Dense fog expected during early morning hours. Drive carefully with low beam lights. Visibility may be less than 100 meters.', 'Dense Fog', 'Low', 'Dindigul', 'admin@disaster.gov.in', 'Active'),
('Landslide Alert', 'Landslide reported on hill roads. Avoid travel to affected areas. Rescue teams deployed.', 'Landslide', 'High', 'Ooty', 'admin@disaster.gov.in', 'Active'),
('Earthquake Tremors', 'Minor earthquake tremors felt in the region. No major damage reported. Stay alert for aftershocks.', 'Earthquake', 'Medium', 'Salem', 'admin@disaster.gov.in', 'Active'),
('Heat Wave Warning', 'Extreme heat conditions expected. Stay hydrated and avoid outdoor activities during peak hours.', 'Heat Wave', 'High', 'Madurai', 'admin@disaster.gov.in', 'Active'),
('Storm Warning', 'Thunderstorm expected with heavy winds. Secure loose objects and stay indoors.', 'Storm', 'Medium', 'Tirunelveli', 'admin@disaster.gov.in', 'Active');

-- ============================================
-- 8. Insert Sample Disasters
-- ============================================
INSERT INTO disasters (type, location, severity, status, timestamp, description) VALUES
('Heavy Rain', 'Chennai', 'High', 'Active', NOW(), 'Heavy rainfall causing waterlogging in several areas. Roads flooded in T Nagar and Mylapore.'),
('Cyclone', 'Tamil Nadu Coastal Areas', 'High', 'Active', DATE_SUB(NOW(), INTERVAL 2 HOUR), 'Cyclone warning issued for coastal regions. Wind speed expected to reach 100 km/h.'),
('Flood', 'Coimbatore', 'Medium', 'Active', DATE_SUB(NOW(), INTERVAL 1 HOUR), 'Flash floods reported in low-lying areas near Noyyal River.'),
('Dense Fog', 'Dindigul', 'Low', 'Active', DATE_SUB(NOW(), INTERVAL 30 MINUTE), 'Dense fog affecting visibility on highways. Drive carefully.'),
('Landslide', 'Ooty', 'High', 'Active', DATE_SUB(NOW(), INTERVAL 3 HOUR), 'Landslide blocking main road to hill station. Traffic diverted.'),
('Earthquake', 'Salem', 'Medium', 'Active', DATE_SUB(NOW(), INTERVAL 4 HOUR), 'Minor earthquake tremors felt, magnitude 3.5. No major damage reported.'),
('Heavy Rain', 'Madurai', 'Medium', 'Active', DATE_SUB(NOW(), INTERVAL 5 HOUR), 'Moderate rainfall expected for next 24 hours.'),
('Flood', 'Tiruchirappalli', 'High', 'Active', DATE_SUB(NOW(), INTERVAL 6 HOUR), 'Cauvery river water level rising. Low-lying areas evacuated.'),
('Dense Fog', 'Tirunelveli', 'Low', 'Active', DATE_SUB(NOW(), INTERVAL 7 HOUR), 'Morning fog reducing visibility on NH-44.'),
('Cyclone', 'Kanyakumari', 'High', 'Active', DATE_SUB(NOW(), INTERVAL 8 HOUR), 'Cyclone approaching southern tip. Fishermen advised not to venture into sea.');

-- ============================================
-- 9. Verification Queries
-- ============================================

-- Show all users with roles
SELECT '=== USERS ===' AS Info;
SELECT id, email, name, role, location FROM users ORDER BY 
    CASE role WHEN 'Admin' THEN 1 WHEN 'Responder' THEN 2 WHEN 'Citizen' THEN 3 END;

-- Show all active alerts
SELECT '=== ACTIVE ALERTS ===' AS Info;
SELECT alert_id, title, disaster_type, severity, region, status, created_at FROM alerts WHERE status = 'Active' ORDER BY created_at DESC;

-- Show all active disasters
SELECT '=== ACTIVE DISASTERS ===' AS Info;
SELECT id, type, location, severity, status, timestamp FROM disasters WHERE status = 'Active' ORDER BY timestamp DESC;

-- Summary counts
SELECT '=== SUMMARY ===' AS Info;
SELECT 
    (SELECT COUNT(*) FROM users) AS Total_Users,
    (SELECT COUNT(*) FROM alerts WHERE status = 'Active') AS Active_Alerts,
    (SELECT COUNT(*) FROM disasters WHERE status = 'Active') AS Active_Disasters;

-- ============================================
-- LOGIN CREDENTIALS
-- ============================================
-- Admin: admin@disaster.gov.in / admin123
-- Responder: responder@disaster.gov.in / responder123
-- Citizen: citizen@gmail.com / citizen123
-- ============================================
