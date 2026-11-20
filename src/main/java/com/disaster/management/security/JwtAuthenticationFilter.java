package com.disaster.management.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String cleanRole = (role != null && !role.trim().isEmpty()) ? role.trim() : "Citizen";
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + cleanRole));

                if ("admin".equalsIgnoreCase(cleanRole)) {
                    if (!"Admin".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_Admin"));
                    if (!"ADMIN".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                } else if ("responder".equalsIgnoreCase(cleanRole)) {
                    if (!"Responder".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_Responder"));
                    if (!"RESPONDER".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_RESPONDER"));
                } else {
                    if (!"Citizen".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_Citizen"));
                    if (!"CITIZEN".equals(cleanRole)) authorities.add(new SimpleGrantedAuthority("ROLE_CITIZEN"));
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    email, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        // Support token in query parameter for EventSource (SSE) and media resource streaming
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken.trim();
        }
        return null;
    }
}
