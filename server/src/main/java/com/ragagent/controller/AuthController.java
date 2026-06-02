package com.ragagent.controller;

import com.ragagent.model.LoginRequest;
import com.ragagent.model.RegisterRequest;
import com.ragagent.model.User;
import com.ragagent.security.JwtUtil;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.UserService;
import com.ragagent.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final JwtAuthFilter jwtAuthFilter;
    private final CartService cartService;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          JwtAuthFilter jwtAuthFilter, CartService cartService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.jwtAuthFilter = jwtAuthFilter;
        this.cartService = cartService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        log.info("==> POST /api/auth/register | username={}", req.getUsername());
        if (req.getUsername() == null || req.getUsername().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        if (req.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少6位"));
        }
        try {
            User user = userService.register(req.getUsername(), req.getPassword());
            return ResponseEntity.ok(Map.of("message", "注册成功", "userId", user.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        log.info("==> POST /api/auth/login | username={}", req.getUsername());
        User user = userService.login(req.getUsername(), req.getPassword());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "username", user.getUsername()));
    }

    @PostMapping("/link-session")
    public ResponseEntity<?> linkSession(@RequestParam String sessionId,
                                          HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        log.info("==> POST /api/auth/link-session | userId={} | sessionId={}", userId, sessionId);
        cartService.migrateCart(sessionId, userId);
        return ResponseEntity.ok(Map.of("message", "会话已绑定"));
    }
}
