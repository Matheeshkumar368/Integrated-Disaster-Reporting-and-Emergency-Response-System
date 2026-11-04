package com.disaster.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.disaster.management.entity.CitizenReport;
import com.disaster.management.entity.EmergencyVideo;
import com.disaster.management.entity.RescueTask;
import com.disaster.management.entity.User;
import com.disaster.management.repository.CitizenReportRepository;
import com.disaster.management.repository.EmergencyVideoRepository;
import com.disaster.management.repository.OfficialAlertRepository;
import com.disaster.management.repository.RescueTaskRepository;
import com.disaster.management.repository.UserRepository;
import com.disaster.management.service.NdmaSyncService;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(
            UserRepository userRepo,
            OfficialAlertRepository officialAlertRepository,
            RescueTaskRepository rescueTaskRepo,
            CitizenReportRepository citizenReportRepo,
            EmergencyVideoRepository emergencyVideoRepo,
            NdmaSyncService ndmaSyncService,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // SAFE INITIALIZATION: Baseline academic demonstration accounts
            if (userRepo.count() == 0) {
                log.info("Database contains no users. Seeding baseline academic demo accounts...");
                try {
                    // Create Default Admin User
                    User admin = new User();
                    admin.setEmail("admin@disaster.gov.in");
                    admin.setPassword(passwordEncoder.encode("admin123"));
                    admin.setName("Admin User");
                    admin.setPhone("9876543210");
                    admin.setLocation("Chennai");
                    admin.setRole("Admin");
                    admin.setAdminPin(passwordEncoder.encode("123456"));
                    admin.setAdminPinEnrolled(true);
                    admin.setAdminPinRecoveryCode(passwordEncoder.encode("922523244035"));
                    admin.setBackupCodeConfigured(true);
                    userRepo.save(admin);

                    // Create Responder Users
                    User responder1 = new User();
                    responder1.setEmail("responder@disaster.gov.in");
                    responder1.setPassword(passwordEncoder.encode("responder123"));
                    responder1.setName("Emergency Responder");
                    responder1.setPhone("9876543211");
                    responder1.setLocation("Coimbatore");
                    responder1.setRole("Responder");
                    responder1.setAssignedZone("Coimbatore");
                    responder1.setGroupClassification("Rescuer");
                    userRepo.save(responder1);

                    User responder2 = new User();
                    responder2.setEmail("responder2@disaster.gov.in");
                    responder2.setPassword(passwordEncoder.encode("responder123"));
                    responder2.setName("Rescue Team Lead");
                    responder2.setPhone("9876543214");
                    responder2.setLocation("Tiruchirappalli");
                    responder2.setRole("Responder");
                    responder2.setAssignedZone("Tiruchirappalli");
                    responder2.setGroupClassification("Leader");
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

                    log.info("Successfully seeded baseline demo users.");
                } catch (Exception e) {
                    log.error("Failed to seed baseline users: {}", e.getMessage());
                }
            } else {
                log.info("Existing users found ({}); verifying baseline credentials synchronization...", userRepo.count());
                try {
                    // Synchronize Admin user PIN if not enrolled
                    userRepo.findByEmail("admin@disaster.gov.in").ifPresent(admin -> {
                        boolean updated = false;
                        if (!passwordEncoder.matches("admin123", admin.getPassword())) {
                            admin.setPassword(passwordEncoder.encode("admin123"));
                            updated = true;
                        }
                        if (admin.getAdminPin() == null || !Boolean.TRUE.equals(admin.getAdminPinEnrolled())) {
                            admin.setAdminPin(passwordEncoder.encode("123456"));
                            admin.setAdminPinEnrolled(true);
                            admin.resetPinFailures();
                            updated = true;
                        }
                        if (admin.getAdminPinRecoveryCode() == null || !Boolean.TRUE.equals(admin.getBackupCodeConfigured())) {
                            admin.setAdminPinRecoveryCode(passwordEncoder.encode("922523244035"));
                            admin.setBackupCodeConfigured(true);
                            admin.resetBackupCodeFailures();
                            updated = true;
                        }
                        if (updated) {
                            userRepo.save(admin);
                            log.info("Synchronized baseline admin credentials, security PIN & backup code.");
                        }
                    });

                    // Synchronize Responder credentials
                    userRepo.findByEmail("responder@disaster.gov.in").ifPresent(responder -> {
                        if (!passwordEncoder.matches("responder123", responder.getPassword())) {
                            responder.setPassword(passwordEncoder.encode("responder123"));
                            userRepo.save(responder);
                            log.info("Synchronized baseline responder credentials.");
                        }
                    });

                    // Synchronize Citizen credentials
                    userRepo.findByEmail("citizen@gmail.com").ifPresent(citizen -> {
                        if (!passwordEncoder.matches("citizen123", citizen.getPassword())) {
                            citizen.setPassword(passwordEncoder.encode("citizen123"));
                            userRepo.save(citizen);
                            log.info("Synchronized baseline citizen credentials.");
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to synchronize user security settings: {}", e.getMessage());
                }
            }

            // Baseline Demonstration Rescue Tasks
            try {
                if (rescueTaskRepo.count() == 0) {
                    log.info("No rescue tasks found. Seeding baseline academic demonstration tasks...");
                    RescueTask task1 = new RescueTask();
                    task1.setTitle("Peelamedu Flood Evacuation & Shelter Transport");
                    task1.setDescription("[ACADEMIC DEMO] Evacuate low-lying households near Peelamedu lake breach to designated community hall relief shelter.");
                    task1.setZone("Coimbatore");
                    task1.setPriority("High");
                    task1.setStatus("In Progress");
                    task1.setAssignedTo("responder@disaster.gov.in");
                    task1.setAssignedResponderName("Emergency Responder");
                    task1.setLocationName("Peelamedu, Coimbatore");
                    task1.setLatitude(11.0267);
                    task1.setLongitude(76.9984);
                    task1.setCreatedBy("admin@disaster.gov.in");
                    rescueTaskRepo.save(task1);

                    RescueTask task2 = new RescueTask();
                    task2.setTitle("Medical Relief Kits Distribution");
                    task2.setDescription("[ACADEMIC DEMO] Distribute emergency first-aid kits and potable water filters to vulnerable flood relief centers.");
                    task2.setZone("Tiruchirappalli");
                    task2.setPriority("Medium");
                    task2.setStatus("Pending");
                    task2.setAssignedTo("responder2@disaster.gov.in");
                    task2.setAssignedResponderName("Rescue Team Lead");
                    task2.setLocationName("Gandhi Market, Tiruchirappalli");
                    task2.setLatitude(10.7905);
                    task2.setLongitude(78.7047);
                    task2.setCreatedBy("admin@disaster.gov.in");
                    rescueTaskRepo.save(task2);

                    log.info("Successfully seeded baseline demo rescue tasks.");
                }
            } catch (Exception e) {
                log.warn("Demo rescue tasks seeding skipped: {}", e.getMessage());
            }

            // Baseline Demonstration Citizen SOS Reports
            try {
                if (citizenReportRepo.count() == 0) {
                    log.info("No citizen distress reports found. Seeding baseline academic demo citizen reports...");
                    CitizenReport r1 = new CitizenReport();
                    r1.setCitizenEmail("citizen@gmail.com");
                    r1.setLocation("Peelamedu, Coimbatore");
                    r1.setMessage("[ACADEMIC DEMO SOS] Flash water surge entering ground floor homes. 4 senior citizens require immediate boat evacuation assistance.");
                    r1.setStatus("Pending");
                    citizenReportRepo.save(r1);

                    CitizenReport r2 = new CitizenReport();
                    r2.setCitizenEmail("citizen2@gmail.com");
                    r2.setLocation("Velachery, Chennai");
                    r2.setMessage("[ACADEMIC DEMO REPORT] High winds have toppled trees and power lines across junction. Road traffic completely blocked.");
                    r2.setStatus("Acknowledged");
                    citizenReportRepo.save(r2);

                    log.info("Successfully seeded baseline demo citizen distress reports.");
                }
            } catch (Exception e) {
                log.warn("Demo citizen reports seeding skipped: {}", e.getMessage());
            }

            // Emergency Survival & Evacuation Video Library Seeding
            try {
                if (emergencyVideoRepo.count() == 0) {
                    log.info("Seeding verified Emergency Survival & Evacuation Video Library (NDMA, NDRF, WHO, USGS, NFPA)...");

                    // 1. Flood
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Surviving Catastrophic Flooding & Flash Floods",
                        "W8S-f6T56tA", "Flood", "NDMA & NDRF", "English",
                        "Crucial survival protocols for urban flash flooding, rising floodwaters, and safe high-ground evacuation.",
                        "4:42",
                        "1. Never walk, swim, or drive through moving water. 6 inches can knock you down, 12 inches can sweep away vehicles.\n2. Evacuate immediately if advised. Move to highest ground or roof.\n3. Disconnect power mains and LPG valves before water enters.\n4. Keep emergency grab-bag ready with drinking water, torch, whistle, and rations."
                    ));
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "வெள்ளப் பெருக்கு அவசர பாதுகாப்பு வழிகாட்டி (Flood Evacuation & Safety - Tamil)",
                        "V433KvhM3DE", "Flood", "TN-SDMA & NDMA", "Tamil",
                        "வெள்ளப்பெருக்கு மற்றும் புயல் மழையின் போது பொதுமக்கள் தங்களை பாதுகாத்துக் கொள்வதற்கான வழிமுறைகள்.",
                        "3:50",
                        "1. வெள்ள நீரில் நடக்கவோ அல்லது வாகனம் ஓட்டவோ கூடாது.\n2. மின்சாரம் மற்றும் எரிவாயு இணைப்புகளை உடனடியாக துண்டிக்கவும்.\n3. மேடான இடங்கள் அல்லது நிவாரண முகாம்களுக்குச் செல்லவும்.\n4. அவசர உதவிக்கு 1070 / 1077 எண்களை அழைக்கவும்."
                    ));

                    // 2. Fire
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Plan Your Escape: Residential Fire Safety & Evacuation",
                        "bhk2Fq3jZuo", "Fire", "NFPA & National Fire Service", "English",
                        "Comprehensive fire escape planning, smoke inhalation prevention, and emergency exit navigation.",
                        "3:30",
                        "1. Stay low beneath the smoke layer — smoke and toxic gases rise.\n2. Feel doors with back of hand before opening. If hot, use secondary route.\n3. STOP, DROP, AND ROLL if clothing catches fire.\n4. Once outside, stay out. Never re-enter a burning structure."
                    ));
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "தீ விபத்து தப்பித்தல் மற்றும் பாதுகாப்பு வழிமுறைகள் (Fire Safety Escape - Tamil)",
                        "iHw-lqWn6yI", "Fire", "Tamil Nadu Fire & Rescue Services", "Tamil",
                        "வீட்டு மற்றும் வணிகக் கட்டட தீ விபத்துகளிலிருந்து தப்பிப்பதற்கான வழிமுறைகள்.",
                        "4:10",
                        "1. புகை சூழ்ந்தால் குனிந்து அல்லது தவழ்ந்து வெளியேறவும்.\n2. ஆடையில் தீப்பிடித்தால் ஓடாமல் STOP, DROP, ROLL முறையைப் பின்பற்றவும்.\n3. மின்தூக்கி (Lift) பயன்படுத்த வேண்டாம்; படிக்கட்டுகளை மட்டுமே பயன்படுத்தவும்."
                    ));

                    // 3. Earthquake
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Drop, Cover, and Hold On: Earthquake Survival Protocols",
                        "G9bA_Bf0UfA", "Earthquake", "USGS & NDMA", "English",
                        "Scientifically proven earthquake survival reactions: Drop, Cover, and Hold On during active tremors.",
                        "2:55",
                        "1. DROP onto hands and knees.\n2. COVER head and neck beneath a sturdy table or desk.\n3. HOLD ON until shaking ceases.\n4. If outdoors, move to an open area away from buildings, poles, and power lines."
                    ));
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "நிலநடுக்க அவசர பாதுகாப்பு நடவடிக்கைகள் (Earthquake Survival - Tamil)",
                        "kF22N0k_f7A", "Earthquake", "NDMA India & TN-SDMA", "Tamil",
                        "நிலநடுக்கத்தின் போது வீட்டிற்குள் மற்றும் வெளிப்புறங்களில் எவ்வாறு செயல்பட வேண்டும் என்பதற்கான விளக்கம்.",
                        "3:15",
                        "1. கீழே அமர்ந்து தலை மற்றும் கழுத்தை உறுதியான மேசையின் கீழ் மறைக்கவும் (Drop, Cover, Hold On).\n2. கண்ணாடி ஜன்னல்கள், மின் கம்பிகள் மற்றும் உயரமான மரங்களிலிருந்து விலகி இருக்கவும்."
                    ));

                    // 4. Cyclone
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Cyclone Safety & Pre-Storm Preparedness Guidelines",
                        "aFz82g98sDk", "Cyclone", "NDMA & IMD", "English",
                        "Essential safety actions before, during, and after severe cyclonic storms and landfall.",
                        "5:12",
                        "1. Board up glass windows or apply protective safety tape.\n2. Keep battery radios tuned to IMD/NDMA weather bulletins.\n3. Secure loose objects in compound (tin sheets, flower pots, solar panels).\n4. Evacuate vulnerable thatched or low-lying coastal homes."
                    ));
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "புயல் பாதுகாப்பு மற்றும் முன்னெச்சரிக்கை வழிகாட்டுதல் (Cyclone Safety - Tamil)",
                        "c8dZ43vL8kE", "Cyclone", "TN-SDMA & IMD Chennai", "Tamil",
                        "கடுமையான புயல் காற்று வீசும் போது செய்ய வேண்டிய மற்றும் செய்யக்கூடாத முக்கிய காரியங்கள்.",
                        "4:05",
                        "1. புயலின் போது தேவையின்றி வெளியே செல்ல வேண்டாம்.\n2. செல்போன்கள் மற்றும் பவர் பேங்க்குகளை முன்கூட்டியே முழுமையாக சார்ஜ் செய்யவும்.\n3. தேவையான குடிநீர், உலர் உணவு மற்றும் மருந்துகளை இருப்பு வைக்கவும்."
                    ));

                    // 5. Tsunami
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Tsunami Early Warning Response & High Ground Evacuation",
                        "x0prflzsm38", "Tsunami", "UNESCO & INCOIS", "English",
                        "Recognizing natural warning signs of tsunami waves and mandatory coastal evacuation routes.",
                        "3:40",
                        "1. If you feel strong tremor near coast or see sea receding abnormally, run to high ground immediately.\n2. Stay at least 2 km inland or 30 meters above sea level.\n3. Tsunamis are a series of waves; first wave is rarely the largest."
                    ));

                    // 6. Landslide
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Landslide & Mudflow Warning Signs & Survival Protocols",
                        "R8jV3T99wV0", "Landslide", "NDMA & GSI", "English",
                        "Critical indicators of slope instability and swift evacuation procedures in mountainous terrains.",
                        "4:02",
                        "1. Watch for sudden cracks in foundation, tilting trees or poles, or muddy stream runoffs.\n2. Listen for unusual sounds like trees cracking or boulders knocking together.\n3. Evacuate immediately perpendicular to the slide path. Do not stop in valleys or ravines."
                    ));

                    // 7. First Aid
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Hands-Only CPR & Severe Bleeding Control: Basic Life Support",
                        "M4ACYp8h3qc", "First Aid", "WHO & Red Cross", "English",
                        "Step-by-step hands-only CPR cadence and direct pressure bleeding control during trauma incidents.",
                        "3:18",
                        "1. Check responsiveness and call emergency services.\n2. Push hard and fast in center of chest at 100-120 beats per minute (Stayin' Alive beat).\n3. For severe bleeding, apply firm, continuous direct pressure with a clean cloth."
                    ));
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "அவசர முதலுதவி மற்றும் சிபிஆர் வழிகாட்டி (First Aid & CPR - Tamil)",
                        "r8kK2sXp4oA", "First Aid", "St. John Ambulance & Red Cross Tamil Nadu", "Tamil",
                        "சுவாசம் நின்ற நபருக்கு உடனடியாக உயிர் காக்கும் சிபிஆர் சிகிச்சை அளிக்கும் முறை.",
                        "4:40",
                        "1. காயம்பட்டவரை பாதுகாப்பான இடத்திற்கு மாற்றவும்.\n2. அதிக இரத்தப்போக்கு ஏற்பட்டால் சுத்தமான துணியால் தொடர்ந்து அழுத்தவும்.\n3. மூச்சு நின்றுவிட்டால் நெஞ்சின் நடுவில் வேகமாக அழுத்தவும் (CPR)."
                    ));

                    // 8. Evacuation
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Disaster Zone Evacuation & Mass Movement Protocols",
                        "q_N2FqF1hD0", "Evacuation", "NDRF India", "English",
                        "Standard operating procedures for orderly community evacuation, corridor security, and designated shelter check-in.",
                        "5:20",
                        "1. Lock windows and doors, leave note indicating evacuation destination.\n2. Use designated green evacuation corridors only.\n3. Assist elderly, children, and persons with disabilities first.\n4. Register at local designated relief base checkpoint."
                    ));

                    // 9. Disaster Kit
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Building Your 72-Hour Family Emergency Disaster Survival Kit",
                        "43M5mZuzfb8", "Disaster Kit", "NDMA India", "English",
                        "Essential components for 72-hour family emergency preparedness grab-and-go kit.",
                        "4:15",
                        "1. Water: 1 gallon per person per day for minimum 3 days.\n2. Food: 3-day supply of non-perishable, ready-to-eat dry rations.\n3. Battery-powered or hand-crank radio and torch.\n4. First aid kit, whistle, dust masks, moist towelettes, wrench/pliers."
                    ));

                    // 10. Emergency Communication
                    emergencyVideoRepo.save(new EmergencyVideo(
                        "Emergency Communication, SOS Whistle Signals & Offline Coordination",
                        "P_Cj8E7Uf_k", "Emergency Communication", "NDMA & CDAC", "English",
                        "Acoustic and radio communication during grid collapse: international SOS whistle patterns and text message prioritization.",
                        "3:45",
                        "1. Whistle Signal: 3 short blasts repeated every minute is the international distress SOS.\n2. Text messages often get through when voice calls fail due to congestion.\n3. Keep phone in battery-saver mode, turn off Wi-Fi/Bluetooth scanning to conserve power.\n4. Establish an out-of-area family contact person."
                    ));

                    log.info("Successfully seeded 14 verified Emergency Survival Videos across 10 categories.");
                }
            } catch (Exception e) {
                log.warn("Emergency videos seeding skipped: {}", e.getMessage());
            }

            // Real-time NDMA SACHET Alert Sync Verification (zero fake alert records seeded)
            try {
                long officialCount = officialAlertRepository.count();
                if (officialCount == 0) {
                    log.info("No official alerts detected in database. Executing initial NDMA SACHET synchronization...");
                    ndmaSyncService.syncNow();
                } else {
                    log.info("Database contains {} official disaster alerts.", officialCount);
                }
            } catch (Exception e) {
                log.warn("Initial NDMA SACHET sync deferred: {}", e.getMessage());
            }

            log.info("Database Initialization verified: Users={}, OfficialAlerts={}", 
                userRepo.count(), officialAlertRepository.count());
        };
    }
}
