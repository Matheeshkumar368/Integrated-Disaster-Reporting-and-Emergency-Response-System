package com.disaster.management.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Central utility for role normalization and role authorization checks.
 * Strict fail-closed design: missing or unknown roles return null / empty authorities.
 * Never defaults an unknown role to Citizen.
 */
public final class RoleUtil {

    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_CITIZEN = "Citizen";
    public static final String ROLE_RESPONDER = "Responder";

    private RoleUtil() {}

    /**
     * Normalizes any role string to canonical form ("Admin", "Citizen", "Responder").
     * Returns null if missing, empty, or unrecognized (Fail-Closed).
     */
    public static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return null;
        }
        String clean = role.trim();
        if (clean.startsWith("ROLE_")) {
            clean = clean.substring(5);
        }
        if ("admin".equalsIgnoreCase(clean)) {
            return ROLE_ADMIN;
        } else if ("responder".equalsIgnoreCase(clean)) {
            return ROLE_RESPONDER;
        } else if ("citizen".equalsIgnoreCase(clean)) {
            return ROLE_CITIZEN;
        } else {
            return null; // Fail closed: never default to Citizen!
        }
    }

    /**
     * Generates standard GrantedAuthorities for Spring Security.
     * If the role is invalid or null, returns an empty list (fail-closed).
     */
    public static List<SimpleGrantedAuthority> toGrantedAuthorities(String role) {
        String canonical = normalizeRole(role);
        if (canonical == null) {
            return Collections.emptyList();
        }
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
        if (targetNormalized == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authRole = ga.getAuthority();
            if (authRole == null) continue;
            if (authRole.startsWith("ROLE_")) {
                authRole = authRole.substring(5);
            }
            String norm = normalizeRole(authRole);
            if (norm != null && norm.equalsIgnoreCase(targetNormalized)) {
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
