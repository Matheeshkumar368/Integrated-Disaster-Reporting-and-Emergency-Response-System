package com.disaster.management.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final String secretKeyString;
    private final long tokenValidityMs;
    private final long stepUpValidityMs;

    public JwtUtil(
            @Value("${app.jwt.secret:disaster-management-academic-super-secure-jwt-secret-key-2026-disaster-response-system-368}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long tokenValidityMs,
            @Value("${app.admin.step-up-duration-minutes:15}") int stepUpMinutes) {
        
        // Ensure secret meets HS256 minimum bit length requirement (256 bits = 32 bytes)
        if (secret == null || secret.length() < 32) {
            this.secretKeyString = "disaster-management-academic-super-secure-jwt-secret-key-2026-disaster-response-system-368";
        } else {
            this.secretKeyString = secret;
        }
        this.tokenValidityMs = tokenValidityMs;
        this.stepUpValidityMs = (long) stepUpMinutes * 60 * 1000L;
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    // Standard Token Generation
    public String generateToken(String email, String role) {
        return generateToken(email, role, null);
    }

    public String generateToken(String email, String role, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (name != null) {
            claims.put("name", name);
        }
        claims.put("type", "ACCESS");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + tokenValidityMs))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // High-Security Short-Lived Step-Up Token for Admin PIN Verification
    public String generateAdminStepUpToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "Admin");
        claims.put("type", "ADMIN_STEP_UP");
        claims.put("scope", "privileged_admin_operations");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + stepUpValidityMs))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Claims Extraction
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(String token) {
        try {
            return extractAllClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            return extractAllClaims(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String extractTokenType(String token) {
        try {
            return extractAllClaims(token).get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isStepUpToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "ADMIN_STEP_UP".equals(claims.get("type")) &&
                   "Admin".equals(claims.get("role")) &&
                   !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateTokenForUser(String token, String expectedEmail) {
        String email = extractEmail(token);
        return email != null && email.equalsIgnoreCase(expectedEmail) && validateToken(token);
    }

    private boolean isTokenExpired(String token) {
        try {
            Date exp = extractAllClaims(token).getExpiration();
            return exp.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
