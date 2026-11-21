package com.disaster.management.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Central utility for role normalization and role authorization checks.
 * Supports canonical roles ("Admin", "Citizen", "Responder") and multiple authority variations.
 */
public final class RoleUtil {

    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_CITIZEN = "Citizen";
    public static final String ROLE_RESPONDER = "Responder";

    private RoleUtil() {}

    /**
     * Normalizes any role string (e.g. "ROLE_ADMIN", "ADMIN", "admin", "Admin") to canonical form.
     */
    public static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ROLE_CITIZEN;
        }
        String clean = role.trim();
        if (clean.startsWith("ROLE_")) {
            clean = clean.substring(5);
        }
        if ("admin".equalsIgnoreCase(clean)) {
            return ROLE_ADMIN;
        } else if ("responder".equalsIgnoreCase(clean)) {
            return ROLE_RESPONDER;
        } else {
            return ROLE_CITIZEN;
        }
    }

    /**
     * Generates standard GrantedAuthorities for Spring Security.
     * Generates both canonical ("ROLE_Admin") and uppercase ("ROLE_ADMIN") for maximum compatibility.
     */
    public static List<SimpleGrantedAuthority> toGrantedAuthorities(String role) {
        String canonical = normalizeRole(role);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + canonical));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + canonical.toUpperCase()));
        return authorities;
    }

    /**
     * Checks if the given authentication contains the target role (case-insensitive, prefix-agnostic).
     */
    public static boolean hasRole(Authentication auth, String targetRole) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        String targetNormalized = normalizeRole(targetRole);
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authRole = ga.getAuthority();
            if (authRole == null) continue;
            if (authRole.startsWith("ROLE_")) {
                authRole = authRole.substring(5);
            }
            if (normalizeRole(authRole).equalsIgnoreCase(targetNormalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin(Authentication auth) {
        return hasRole(auth, ROLE_ADMIN);
    }

    public static boolean isResponder(Authentication auth) {
        return hasRole(auth, ROLE_RESPONDER);
    }

    public static boolean isCitizen(Authentication auth) {
        return hasRole(auth, ROLE_CITIZEN);
    }

    public static String getRole(Authentication auth) {
        if (isAdmin(auth)) return ROLE_ADMIN;
        if (isResponder(auth)) return ROLE_RESPONDER;
        if (isCitizen(auth)) return ROLE_CITIZEN;
        return null;
    }
}
