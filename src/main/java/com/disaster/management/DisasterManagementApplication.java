package com.disaster.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.disaster.management.service.DisasterService;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DisasterManagementApplication {

    private static final Logger log = LoggerFactory.getLogger(DisasterManagementApplication.class);

    @Autowired
    private DisasterService disasterService;

    public static void main(String[] args) {
        SpringApplication.run(DisasterManagementApplication.class, args);
    }

    @PostConstruct
    public void onStartup() {
        log.info("Disaster Management Application starting up...");
        // Launch external NDMA feed ingestion asynchronously to avoid blocking server readiness
        disasterService.fetchNDMAAlertsAsync();
    }
}
