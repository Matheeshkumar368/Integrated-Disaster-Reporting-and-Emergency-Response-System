package com.disaster.management.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.disaster.management.entity.User;
import com.disaster.management.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            // 1. Verify that the token is not an ADMIN_STEP_UP token (step-up cannot be used for general auth)
            String tokenType = jwtUtil.extractTokenType(token);
            if (jwtUtil.isStepUpToken(token) || "ADMIN_STEP_UP".equalsIgnoreCase(tokenType)) {
                log.warn("Rejected step-up token used for general auth: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Extract authenticated email and claimed role from JWT
            String email = jwtUtil.extractEmail(token);
            String tokenRole = jwtUtil.extractRole(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 3. Load corresponding user from MySQL database
                Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());

                if (userOpt.isEmpty()) {
                    log.warn("JWT rejected: user email '{}' does not exist in database", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userOpt.get();

                // 4. Verify that the user is active
                if (user.getActive() != null && !user.getActive()) {
                    log.warn("JWT rejected: user account '{}' is deactivated", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 5. Compare the token role against the current database role
                String dbRole = RoleUtil.normalizeRole(user.getRole());
                String normTokenRole = RoleUtil.normalizeRole(tokenRole);

                if (dbRole == null) {
                    log.warn("JWT rejected: database role '{}' for user '{}' is invalid", user.getRole(), email);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (!dbRole.equalsIgnoreCase(normTokenRole)) {
                    log.warn("JWT rejected: role mismatch for user '{}'. Token claims '{}', database role is '{}'", email, tokenRole, dbRole);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 6. Generate Spring Security authorities strictly from the verified database role
                List<SimpleGrantedAuthority> authorities = RoleUtil.toGrantedAuthorities(dbRole);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, authorities
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
        // Support token in query parameter for EventSource (SSE) and media streaming
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken.trim();
        }
        return null;
    }
}
