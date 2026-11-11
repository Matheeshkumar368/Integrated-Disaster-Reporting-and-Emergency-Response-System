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
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/alerts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/emergency-videos/**").permitAll()

                // Admin-Only Privileged Endpoints
                // Responder Task Actions (Acknowledge & Resolve)
                .requestMatchers("/api/tasks/*/acknowledge", "/api/tasks/*/resolve").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/tasks/my", "/api/tasks/my-tasks").hasAnyRole("Responder", "Admin")

                // Admin-only Rescue Task & Alert Management
                .requestMatchers(HttpMethod.POST, "/api/admin/ndma/sync").hasRole("Admin")
                .requestMatchers(HttpMethod.POST, "/api/alerts").hasRole("Admin")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/broadcast").hasRole("Admin")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/media").hasRole("Admin")
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/resolve").hasRole("Admin")
                .requestMatchers(HttpMethod.POST, "/api/alerts/*/resolve").hasRole("Admin")
                .requestMatchers(HttpMethod.DELETE, "/api/alerts/**").hasRole("Admin")
                .requestMatchers(HttpMethod.POST, "/api/tasks").hasRole("Admin")
                .requestMatchers(HttpMethod.POST, "/api/tasks/*/assign").hasRole("Admin")
                .requestMatchers(HttpMethod.PUT, "/api/tasks/*/assign").hasRole("Admin")
                .requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasRole("Admin")
                .requestMatchers(HttpMethod.POST, "/api/emergency-videos/**").hasRole("Admin")
                .requestMatchers(HttpMethod.PUT, "/api/emergency-videos/**").hasRole("Admin")
                .requestMatchers(HttpMethod.DELETE, "/api/emergency-videos/**").hasRole("Admin")
                .requestMatchers("/api/reports/export/**").hasRole("Admin")
                .requestMatchers("/api/auth/admin/**").hasRole("Admin")
                .requestMatchers("/api/admin/**").hasRole("Admin")
                .requestMatchers("/api/auth/responders/**").hasRole("Admin")
                .requestMatchers("/api/auth/assign-zone").hasRole("Admin")

                // Citizen Reporting
                .requestMatchers(HttpMethod.POST, "/api/citizen-reports").hasAnyRole("Citizen", "Admin")
                .requestMatchers("/api/citizen-reports/my-reports").hasAnyRole("Citizen", "Admin")
                .requestMatchers(HttpMethod.GET, "/api/citizen-reports", "/api/citizen-reports/paged").hasAnyRole("Admin", "Responder")

                // Responder & Admin Rescue Operations
                .requestMatchers(HttpMethod.PUT, "/api/alerts/*/acknowledge").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/citizen-reports/pending").hasAnyRole("Responder", "Admin")
                .requestMatchers(HttpMethod.PUT, "/api/citizen-reports/*/acknowledge").hasAnyRole("Responder", "Admin")
                .requestMatchers(HttpMethod.PUT, "/api/citizen-reports/*/resolve").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/reports/my-reports").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/tasks/my-tasks").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/auth/responder/**").hasAnyRole("Responder", "Admin")
                .requestMatchers("/api/responder/location/**").hasAnyRole("Responder", "Admin")

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
                .requestMatchers("/api/messages", "/api/messages/**").hasAnyRole("Citizen", "Responder", "Admin")
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
