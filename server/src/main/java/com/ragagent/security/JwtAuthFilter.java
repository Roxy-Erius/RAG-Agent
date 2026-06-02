package com.ragagent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

    public Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        try {
            return jwtUtil.validateToken(token) ? jwtUtil.getUserId(token) : null;
        } catch (Exception e) { return null; }
    }
}
