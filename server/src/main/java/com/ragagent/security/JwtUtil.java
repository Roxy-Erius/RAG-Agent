package com.ragagent.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key = Keys.hmacShaKeyFor(
        "rag-agent-2026-jwt-secret-key-min-32bytes!!".getBytes(StandardCharsets.UTF_8));
    private final long expirationMs = 7 * 24 * 60 * 60 * 1000L;

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, "USER");
    }

    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role != null ? role : "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }

    /** 取角色 claim；老 token 无此 claim 时返回 "USER" */
    public String getRole(String token) {
        Object role = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().get("role");
        return role != null ? role.toString() : "USER";
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) { return false; }
    }
}
