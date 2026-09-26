package com.ragagent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

    public Long getUserId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;
        try {
            return jwtUtil.validateToken(token) ? jwtUtil.getUserId(token) : null;
        } catch (Exception e) { return null; }
    }

    /** 取角色（"USER" / "ADMIN"）；无 token / 非法 token 返回 null */
    public String getRole(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;
        try {
            return jwtUtil.validateToken(token) ? jwtUtil.getRole(token) : null;
        } catch (Exception e) { return null; }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7);
    }
}
