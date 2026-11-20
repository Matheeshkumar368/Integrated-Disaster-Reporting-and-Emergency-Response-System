package com.disaster.management.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        String uploadPath = uploadDir.toUri().toString();
        if (!uploadPath.endsWith("/")) {
            uploadPath += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Admin routes
        registry.addViewController("/admin-dashboard").setViewName("forward:/admin-dashboard.html");
        registry.addViewController("/admin-users").setViewName("forward:/admin-users.html");
        registry.addViewController("/admin-tasks").setViewName("forward:/admin-tasks.html");
        registry.addViewController("/admin-alerts").setViewName("forward:/admin-alerts.html");
        registry.addViewController("/admin-notifications").setViewName("forward:/admin-notifications.html");
        registry.addViewController("/admin-profile").setViewName("forward:/admin-profile.html");

        // Citizen routes
        registry.addViewController("/citizen-dashboard").setViewName("forward:/citizen-dashboard.html");
        registry.addViewController("/citizen-notifications").setViewName("forward:/citizen-notifications.html");

        // Responder routes
        registry.addViewController("/responder-dashboard").setViewName("forward:/responder-dashboard.html");
        registry.addViewController("/responder-tasks").setViewName("forward:/responder-tasks.html");
        registry.addViewController("/responder-rescue").setViewName("forward:/responder-rescue.html");
        registry.addViewController("/responder-notifications").setViewName("forward:/responder-notifications.html");
        registry.addViewController("/responder-profile").setViewName("forward:/responder-profile.html");
        registry.addViewController("/responder-register").setViewName("forward:/responder-register.html");

        // General / Shared routes
        registry.addViewController("/reports").setViewName("forward:/reports.html");
        registry.addViewController("/profile").setViewName("forward:/profile.html");
        registry.addViewController("/alerts").setViewName("forward:/alerts.html");
        registry.addViewController("/training").setViewName("forward:/training.html");
        registry.addViewController("/home").setViewName("forward:/home.html");
        registry.addViewController("/emergency-videos").setViewName("forward:/emergency-videos.html");
        registry.addViewController("/offline").setViewName("forward:/offline.html");
        registry.addViewController("/index-new-login").setViewName("forward:/index-new-login.html");
        registry.addViewController("/toast_showcase").setViewName("forward:/toast_showcase.html");
        registry.addViewController("/index").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/register").setViewName("forward:/index.html");
    }
}
