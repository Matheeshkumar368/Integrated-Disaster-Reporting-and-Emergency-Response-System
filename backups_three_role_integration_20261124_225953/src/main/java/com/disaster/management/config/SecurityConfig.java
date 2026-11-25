package com.disaster.management.config;

import java.util.Arrays;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.disaster.management.repository.UserRepository;
import com.disaster.management.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username.trim().toLowerCase())
                .map(user -> {
                    String roleName = user.getRole() != null ? user.getRole().trim() : "Citizen";
                    return org.springframework.security.core.userdetails.User.builder()
                            .username(user.getEmail())
                            .password(user.getPassword())
                            .roles(roleName)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized: Authentication token is missing or invalid\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden: Insufficient privileges for this resource\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Static web resources & HTML pages (Public)
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/*.html",
                    "/*.css",
                    "/*.js",
                    "/js/**",
                    "/admin-dashboard",
                    "/admin-users",
                    "/admin-tasks",
                    "/admin-alerts",
                    "/admin-notifications",
                    "/admin-profile",
                    "/citizen-dashboard",
                    "/citizen-notifications",
                    "/responder-dashboard",
                    "/responder-tasks",
                    "/responder-rescue",
                    "/responder-notifications",
                    "/responder-profile",
                    "/responder-register",
                    "/reports",
                    "/profile",
                    "/alerts",
                    "/training",
                    "/home",
                    "/emergency-videos",
                    "/offline",
                    "/index-new-login",
                    "/toast_showcase",
                    "/index",
                    "/login",
                    "/register",
                    "/manifest.json",
                    "/manifest.webmanifest",
                    "/sw.js",
                    "/icons/**",
                    "/assets/**",
                    "/loading-screen/**",
                    "/login-designs/**",
                    "/favicon.ico",
                    "/uploads/**",
                    "/audio/**"
                ).permitAll()

                // Public Media Streaming / Downloading (photos, videos rendered in browser)
                .requestMatchers(
                    "/error",
                    "/api/media/file/**",
                    "/api/media/*/download",
                    "/api/media/recent"
                ).permitAll()

                // Public Authentication Endpoints
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/register-responder",
                    "/api/auth/sos-event",
                    "/api/auth/admin/security-pin/recover-with-backup-code",
                    "/api/auth/admin/pin/recover-with-backup-code"
                ).permitAll()

                // Public Disaster Information & Health
                .requestMatchers(
                    "/api/alerts/broadcasted",
                    "/api/alerts/region/**",
                    "/api/official-alerts/**",
                    "/api/disasters/**",
                    "/api/weather/**",
                    "/api/system/**",
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/alerts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/emergency-videos/**").permitAll()

                // Admin-Only Privileged Endpoints
                // Responder Task Actions (Acknowledge & Resolve)
                .requestMatchers("/api/tasks/*/acknowledge", "/api/tasks/*/resolve").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/tasks/my", "/api/tasks/my-tasks").hasAnyRole("Responder", "Admin")

                // Responder & Admin Alert Actions (Acknowledge & Resolve) MUST precede wildcard alert matchers
                .requestMatchers(HttpMethod.POST, "/api/alerts/*/acknowledge", "/api/alerts/*/resolve").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/acknowledge", "/api/alerts/*/resolve").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")

                // Admin Alert Actions (Broadcast, Media, Withdraw)
                .requestMatchers(HttpMethod.POST, "/api/alerts/*/broadcast").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/broadcast").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/media", "/api/alerts/*/withdraw").hasAnyRole("Admin", "ADMIN")

                // Admin-only Rescue Task & Alert Management
                .requestMatchers(HttpMethod.POST, "/api/admin/ndma/sync").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/alerts", "/api/alerts/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*", "/api/alerts/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/alerts/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/tasks").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/tasks/*/assign").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/tasks/*/assign").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/emergency-videos/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/emergency-videos/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/emergency-videos/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/reports/export/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/auth/admin/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/auth/users", "/api/auth/users/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/auth/responders/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/auth/assign-zone").hasAnyRole("Admin", "ADMIN")

                // Citizen Reporting (Strictly Citizen role)
                .requestMatchers(HttpMethod.POST, "/api/citizen-reports").hasAnyRole("Citizen", "CITIZEN")
                .requestMatchers("/api/citizen-reports/my-reports").hasAnyRole("Citizen", "CITIZEN")
                .requestMatchers(HttpMethod.GET, "/api/citizen-reports", "/api/citizen-reports/paged").hasAnyRole("Admin", "ADMIN", "Responder", "RESPONDER")

                // Responder & Admin Rescue Operations
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/acknowledge").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/citizen-reports/pending").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/citizen-reports/*/acknowledge").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/citizen-reports/*/resolve").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/reports/my-reports").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/tasks/my-tasks").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/auth/responder/**").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/responder/location/**").hasAnyRole("Responder", "RESPONDER", "Admin", "ADMIN")

                // Live Tracking, Incident Room, Media Management & Routing
                .requestMatchers(HttpMethod.GET, "/api/media/file/**", "/api/media/*/download").permitAll()
                .requestMatchers("/api/tracking/**").authenticated()
                .requestMatchers("/api/media/**").authenticated()
                .requestMatchers("/api/incidents/**").authenticated()
                .requestMatchers("/api/routing/**").authenticated()

                // General Authenticated Endpoints
                .requestMatchers("/api/citizen-reports/**").authenticated()
                .requestMatchers("/api/reports/**").authenticated()
                .requestMatchers("/api/tasks/**").authenticated()
                // Role-Specific Notification Endpoints with Strict Role Isolation
                .requestMatchers("/api/notifications/admin", "/api/notifications/admin/**").hasAnyRole("Admin", "ADMIN")
                .requestMatchers("/api/notifications/citizen", "/api/notifications/citizen/**").hasAnyRole("Citizen", "CITIZEN")
                .requestMatchers("/api/notifications/responder", "/api/notifications/responder/**").hasAnyRole("Responder", "RESPONDER")
                .requestMatchers("/api/messages", "/api/messages/**").hasAnyRole("Citizen", "CITIZEN", "Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/notifications", "/api/notifications/**").hasAnyRole("Citizen", "CITIZEN", "Responder", "RESPONDER", "Admin", "ADMIN")
                .requestMatchers("/api/auth/profile").authenticated()
                .requestMatchers("/api/auth/update-profile").authenticated()
                .requestMatchers("/api/auth/sos-settings/**").authenticated()
                .requestMatchers("/api/alerts").authenticated()

                // Any other request requires valid authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "X-Admin-Step-Up-Token"
        ));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Admin-Step-Up-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
