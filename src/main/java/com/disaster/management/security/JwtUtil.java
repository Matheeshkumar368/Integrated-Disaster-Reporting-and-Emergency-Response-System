package com.disaster.management.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET = System.getenv("JWT_SECRET") != null ? System.getenv("JWT_SECRET") : "defaultfallbacksecretkey12345678901234";

    private Key getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    public String generateToken(String email, String role) {
            return Jwts.builder()
                    .setSubject(email)
                    .claim("role", role)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(getKey(), SignatureAlgorithm.HS256)
                    .compact();
        }

        public String extractEmail(String token) {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        }

        public String extractRole(String token) {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("role", String.class);
        }

        public boolean validateToken(String token) {
            try {
                Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

}
