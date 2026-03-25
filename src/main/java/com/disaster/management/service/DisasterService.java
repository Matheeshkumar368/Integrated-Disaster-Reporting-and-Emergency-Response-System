package com.disaster.management.service;

import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.disaster.management.entity.Disaster;
import com.disaster.management.repository.DisasterRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

@Service
public class DisasterService {

    @Autowired
    private DisasterRepository disasterRepository;

    public void fetchNDMAAlerts() {
        try {
            System.out.println("===== Starting to fetch NDMA alerts =====");

            URL url = URI.create("https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml").toURL();

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(url));

            List<SyndEntry> entries = feed.getEntries();

            System.out.println("Found " + entries.size() + " entries in NDMA feed");

            for (SyndEntry entry : entries) {
                try {
                    String title = entry.getTitle() != null ? entry.getTitle() : "";
                    String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                    LocalDateTime time = entry.getPublishedDate() != null ?
                            entry.getPublishedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() :
                            LocalDateTime.now();

                    // 1. Determine type
                    String type = "Other";
                    String fullText = (title + " " + description).toLowerCase();

                    if (fullText.contains("flood")) {
                        type = "Flood";
                    } else if (fullText.contains("cyclone") || fullText.contains("storm")) {
                        type = "Cyclone";
                    } else if (fullText.contains("earthquake")) {
                        type = "Earthquake";
                    } else if (fullText.contains("rain") || fullText.contains("heavy rainfall") || fullText.contains("thunderstorm")) {
                        type = "Heavy Rain";
                    } else if (fullText.contains("fog") || fullText.contains("dense fog")) {
                        type = "Dense Fog";
                    }

                    // 2. Extract real location (this is the most important part)
                    String location = extractLocation(title, description);

                    // 3. Avoid duplicates - check by type and location with null safety
                    if (type == null) type = "Unknown";
                    if (location == null) location = "Unknown";
                    
                    // Check for existing disasters to avoid duplicates
                    List<Disaster> existingList = disasterRepository.findAll();
                    boolean exists = false;
                    for (Disaster d : existingList) {
                        if (type.equals(d.getType()) && location.equals(d.getLocation())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        Disaster disaster = new Disaster();
                        disaster.setType(type);
                        disaster.setLocation(location);
                        disaster.setSeverity("Medium");
                        disaster.setTimestamp(time);
                        disaster.setStatus("Active");

                        disasterRepository.save(disaster);

                        // Print to see what was saved
                        System.out.println("Saved: " + type + " | " + location + " | " + time);
                    }
                } catch (Exception entryEx) {
                    System.out.println("Warning: Could not process entry: " + entryEx.getMessage());
                }
            }

            System.out.println("===== NDMA fetch finished =====");
        } catch (Exception e) {
            System.out.println("Error fetching NDMA alerts: " + e.getMessage());
            // Don't print stack trace for network errors - just log the message
            String errorMsg = e.getMessage();
            if (errorMsg != null && !errorMsg.contains("Connection") && !errorMsg.contains("timeout")) {
                e.printStackTrace();
            }
        }
    }

    // New helper method - this tries to find real places
    private String extractLocation(String title, String description) {
        String text = (title + " " + description).toLowerCase();

        // Tamil Nadu districts (focus on your area)
        if (text.contains("coimbatore")) return "Coimbatore, Tamil Nadu";
        if (text.contains("nilgiris") || text.contains("the nilgiris")) return "Nilgiris, Tamil Nadu";
        if (text.contains("dindigul")) return "Dindigul, Tamil Nadu";
        if (text.contains("theni")) return "Theni, Tamil Nadu";
        if (text.contains("tirunelveli") || text.contains("virudhunagar")) return "Tirunelveli/Virudhunagar, Tamil Nadu";
        if (text.contains("chennai")) return "Chennai, Tamil Nadu";
        if (text.contains("madurai")) return "Madurai, Tamil Nadu";

        // Other states
        if (text.contains("tamil nadu") || text.contains("tn")) return "Tamil Nadu";
        if (text.contains("andhra pradesh") || text.contains("andhra")) return "Andhra Pradesh";
        if (text.contains("kerala")) return "Kerala";
        if (text.contains("sikkim")) return "Sikkim";
        if (text.contains("odisha") || text.contains("orissa")) return "Odisha";

        // Default if nothing matched
        return "India - Check details";
    }

    public List<Disaster> getAllDisasters() {
        return disasterRepository.findAll();
    }
}