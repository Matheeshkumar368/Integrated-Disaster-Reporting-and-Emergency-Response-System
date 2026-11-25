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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DisasterManagementApplication {

    private static final Logger log = LoggerFactory.getLogger(DisasterManagementApplication.class);

    @Autowired
    private DisasterService disasterService;

    public static void main(String[] args) {
        loadEnvironmentSecrets();
        SpringApplication.run(DisasterManagementApplication.class, args);
    }

    /**
     * Resiliently loads database and security secrets across environments:
     * 1. Checks JVM system properties (already set via CLI/IDE)
     * 2. Checks OS environment variables (System.getenv)
     * 3. Checks root and working directory .env files
     * 4. Checks local application-local.properties files
     * 5. Queries Windows User Environment registry (HKCU\Environment) if on Windows
     *
     * Never exposes, prints, or logs secret values.
     */
    private static void loadEnvironmentSecrets() {
        String existingPass = System.getProperty("DB_PASSWORD");
        if (existingPass == null || existingPass.isBlank()) {
            existingPass = System.getProperty("spring.datasource.password");
        }
        if (existingPass == null || existingPass.isBlank()) {
            existingPass = System.getenv("DB_PASSWORD");
        }
        if (existingPass == null || existingPass.isBlank()) {
            existingPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
        }

        if (existingPass != null && !existingPass.isBlank()) {
            System.setProperty("DB_PASSWORD", existingPass);
            System.setProperty("spring.datasource.password", existingPass);
            return;
        }

        // 1. Try reading from .env file in root or user.dir
        String resolvedPassword = tryReadPasswordFromEnvFile(new File(".env"));
        if (resolvedPassword == null) {
            resolvedPassword = tryReadPasswordFromEnvFile(new File(System.getProperty("user.dir"), ".env"));
        }

        // 2. Try reading from local properties file
        if (resolvedPassword == null) {
            resolvedPassword = tryReadPasswordFromProperties(new File("application-local.properties"));
        }
        if (resolvedPassword == null) {
            resolvedPassword = tryReadPasswordFromProperties(new File("src/main/resources/application-local.properties"));
        }

        // 3. Fallback: On Windows, query HKCU\Environment registry for user-scoped DB_PASSWORD
        if (resolvedPassword == null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            resolvedPassword = queryWindowsUserEnv("DB_PASSWORD");
        }

        if (resolvedPassword != null && !resolvedPassword.isBlank()) {
            System.setProperty("DB_PASSWORD", resolvedPassword);
            System.setProperty("spring.datasource.password", resolvedPassword);
            log.info("Database credentials securely loaded from local environment provider.");
        }
    }

    private static String tryReadPasswordFromEnvFile(File file) {
        if (!file.exists() || !file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || !line.contains("=")) continue;
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                if ("DB_PASSWORD".equalsIgnoreCase(key) || "spring.datasource.password".equalsIgnoreCase(key)) {
                    if (!val.isBlank()) return val;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String tryReadPasswordFromProperties(File file) {
        if (!file.exists() || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String p = props.getProperty("spring.datasource.password");
            if (p == null || p.isBlank()) p = props.getProperty("DB_PASSWORD");
            if (p != null && !p.isBlank()) return p.trim();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String queryWindowsUserEnv(String varName) {
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", varName)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("REG_SZ")) {
                        int idx = line.indexOf("REG_SZ");
                        return line.substring(idx + 6).trim();
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        }
        return null;
    }

    @PostConstruct
    public void onStartup() {
        log.info("Disaster Management Application starting up...");
        // Launch external NDMA feed ingestion asynchronously to avoid blocking server readiness
        disasterService.fetchNDMAAlertsAsync();
    }
}
