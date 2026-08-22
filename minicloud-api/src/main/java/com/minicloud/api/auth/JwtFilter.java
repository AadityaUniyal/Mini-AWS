package com.minicloud.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Map<String, Object> claims = jwtUtil.extractAllClaims(token);
            String username = jwtUtil.extractUsername(token);
            if (username == null || username.isBlank()) {
                username = (String) claims.get("username");
            }
            if (username == null || username.isBlank()) {
                username = (String) claims.get("email");
            }

            String role = (String) claims.get("role");
            String userIdStr = (String) claims.get("userId");
            String accountId = (String) claims.get("accountId");
            boolean isRoot = Boolean.TRUE.equals(claims.get("rootUser")) || Boolean.TRUE.equals(claims.get("isRoot"));

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }

                UUID userId = null;
                try {
                    if (userIdStr != null) {
                        userId = UUID.fromString(userIdStr);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId format in token: {}", userIdStr);
                }

                UserPrincipal principal = new UserPrincipal(username, userId, accountId, role, isRoot);
                
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
