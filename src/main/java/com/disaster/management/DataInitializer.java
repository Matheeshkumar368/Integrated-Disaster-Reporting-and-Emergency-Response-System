package com.disaster.management;

import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.disaster.management.entity.Alert;
import com.disaster.management.entity.Disaster;
import com.disaster.management.entity.User;
import com.disaster.management.repository.AlertRepository;
import com.disaster.management.repository.DisasterRepository;
import com.disaster.management.repository.UserRepository;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo, AlertRepository alertRepo, DisasterRepository disasterRepo, PasswordEncoder passwordEncoder) {
        return args -> {
            // Clear existing data in proper order to avoid foreign key issues
            try {
                alertRepo.deleteAll();
            } catch (Exception e) {
                System.out.println("Warning: Could not delete alerts: " + e.getMessage());
            }
            try {
                disasterRepo.deleteAll();
            } catch (Exception e) {
                System.out.println("Warning: Could not delete disasters: " + e.getMessage());
            }
            try {
                userRepo.deleteAll();
            } catch (Exception e) {
                System.out.println("Warning: Could not delete users: " + e.getMessage());
            }
            
            // ============================================================
            // DEVELOPMENT ONLY - SEED DATA
            // These users are created for development/testing purposes only.
            // Passwords are BCrypt hashed below for security compliance.
            // ============================================================

            try {
                // Create Admin User
                User admin = new User();
                admin.setEmail("admin@disaster.gov.in");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setName("Admin User");
                admin.setPhone("9876543210");
                admin.setLocation("Chennai");
                admin.setRole("Admin");
                userRepo.save(admin);

                // Create Responder Users
                User responder1 = new User();
                responder1.setEmail("responder@disaster.gov.in");
                responder1.setPassword(passwordEncoder.encode("responder123"));
                responder1.setName("Emergency Responder");
                responder1.setPhone("9876543211");
                responder1.setLocation("Coimbatore");
                responder1.setRole("Responder");
                responder1.setAssignedZone("Coimbatore"); // Assigned to Coimbatore zone
                userRepo.save(responder1);

                User responder2 = new User();
                responder2.setEmail("responder2@disaster.gov.in");
                responder2.setPassword(passwordEncoder.encode("responder123"));
                responder2.setName("Rescue Team Lead");
                responder2.setPhone("9876543214");
                responder2.setLocation("Tiruchirappalli");
                responder2.setRole("Responder");
                responder2.setAssignedZone("Tiruchirappalli"); // Assigned to Tiruchirappalli zone
                userRepo.save(responder2);

                // Create Citizen Users
                User citizen1 = new User();
                citizen1.setEmail("citizen@gmail.com");
                citizen1.setPassword(passwordEncoder.encode("citizen123"));
                citizen1.setName("John Citizen");
                citizen1.setPhone("9876543212");
                citizen1.setLocation("Madurai");
                citizen1.setRole("Citizen");
                userRepo.save(citizen1);

                User citizen2 = new User();
                citizen2.setEmail("citizen2@gmail.com");
                citizen2.setPassword(passwordEncoder.encode("citizen123"));
                citizen2.setName("Jane Citizen");
                citizen2.setPhone("9876543213");
                citizen2.setLocation("Chennai");
                citizen2.setRole("Citizen");
                userRepo.save(citizen2);
            } catch (Exception e) {
                System.out.println("Warning: Could not create users: " + e.getMessage());
            }

            try {
                // Create Sample Alerts
                String[][] alertsData = {
                    {"Heavy Rainfall Warning", "Heavy rainfall expected in the next 24 hours. Stay indoors and avoid unnecessary travel.", "Heavy Rain", "High", "Chennai"},
                    {"Cyclone Alert", "Severe cyclone approaching coastal areas. Evacuate low-lying regions immediately.", "Cyclone", "High", "Tamil Nadu Coast"},
                    {"Flood Warning", "River water levels rising rapidly. Residents near riverbanks should move to higher ground.", "Flood", "Medium", "Coimbatore"},
                    {"Dense Fog Advisory", "Dense fog expected during early morning hours. Drive carefully with low beam lights.", "Dense Fog", "Low", "Dindigul"},
                    {"Landslide Alert", "Landslide reported on hill roads. Avoid travel to affected areas.", "Landslide", "High", "Ooty"},
                    {"Earthquake Tremors", "Minor earthquake tremors felt. No major damage reported. Stay alert.", "Earthquake", "Medium", "Salem"},
                    {"Heat Wave Warning", "Extreme heat conditions expected. Stay hydrated and avoid outdoor activities.", "Heat Wave", "High", "Madurai"},
                    {"Storm Warning", "Thunderstorm expected with heavy winds. Secure loose objects.", "Storm", "Medium", "Tirunelveli"},
                    {"Flash Flood Alert", "Flash flood warning for low-lying areas. Move to higher ground immediately.", "Flood", "High", "Tiruchirappalli"},
                    {"Tsunami Warning", "Tsunami alert issued for coastal regions. Evacuate immediately to safe zones.", "Tsunami", "High", "Kanyakumari"},
                    {"Fire Alert", "Forest fire reported in the region. Avoid the area and follow evacuation orders.", "Fire", "High", "Coimbatore"},
                    {"Road Blockade", "Major road blocked due to landslide. Use alternate routes.", "Other", "Medium", "Ooty"}
                };

                for (String[] data : alertsData) {
                    Alert alert = new Alert();
                    alert.setTitle(data[0]);
                    alert.setMessage(data[1]);
                    alert.setDisasterType(data[2]);
                    alert.setSeverity(data[3]);
                    alert.setRegion(data[4]);
                    alert.setCreatedBy("admin@disaster.gov.in");
                    alert.setStatus("Active");
                    alert.setCreatedAt(LocalDateTime.now());
                    alertRepo.save(alert);
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not create alerts: " + e.getMessage());
            }

            try {
                // Create Sample Disasters
                String[][] disastersData = {
                    {"Heavy Rain", "Chennai", "High"},
                    {"Cyclone", "Tamil Nadu Coastal Areas", "High"},
                    {"Flood", "Coimbatore", "Medium"},
                    {"Dense Fog", "Dindigul", "Low"},
                    {"Landslide", "Ooty", "High"},
                    {"Earthquake", "Salem", "Medium"},
                    {"Heavy Rain", "Madurai", "Medium"},
                    {"Flood", "Tiruchirappalli", "High"},
                    {"Dense Fog", "Tirunelveli", "Low"},
                    {"Cyclone", "Kanyakumari", "High"},
                    {"Flash Flood", "Tiruchirappalli", "High"},
                    {"Tsunami", "Kanyakumari", "High"},
                    {"Fire", "Coimbatore", "High"},
                    {"Landslide", "Ooty", "High"},
                    {"Heavy Rain", "Chennai", "High"},
                    {"Flood", "Madurai", "Medium"},
                    {"Storm", "Tirunelveli", "Medium"},
                    {"Earthquake", "Vellore", "Medium"},
                    {"Cyclone", "Chennai", "High"},
                    {"Dense Fog", "Salem", "Low"}
                };

                for (String[] data : disastersData) {
                    Disaster disaster = new Disaster();
                    disaster.setType(data[0]);
                    disaster.setLocation(data[1]);
                    disaster.setSeverity(data[2]);
                    disaster.setStatus("Active");
                    disaster.setTimestamp(LocalDateTime.now());
                    disasterRepo.save(disaster);
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not create disasters: " + e.getMessage());
            }

            System.out.println("=== Sample Data Inserted Successfully ===");
            System.out.println("Users: " + userRepo.count());
            System.out.println("Alerts: " + alertRepo.count());
            System.out.println("Disasters: " + disasterRepo.count());
        };
    }
}
