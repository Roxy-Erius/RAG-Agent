package com.ragagent.controller;

import com.ragagent.repository.UserBehaviorRepository;
import com.ragagent.security.JwtAuthFilter;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BehaviorController {
    private static final Logger log = LoggerFactory.getLogger(BehaviorController.class);
    private final UserBehaviorRepository behaviorRepo;
    private final JwtAuthFilter jwtAuthFilter;

    public BehaviorController(UserBehaviorRepository behaviorRepo, JwtAuthFilter jwtAuthFilter) {
        this.behaviorRepo = behaviorRepo;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @PostMapping("/behaviors")
    public ResponseEntity<?> recordBehavior(@RequestBody String body, HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            // Not logged in — silently ignore
            return ResponseEntity.ok().build();
        }

        try {
            JsonObject json = new com.google.gson.Gson().fromJson(body, JsonObject.class);
            String productId = json.has("productId") ? json.get("productId").getAsString() : null;
            String actionType = json.has("actionType") ? json.get("actionType").getAsString() : null;

            if (productId == null || actionType == null) {
                return ResponseEntity.badRequest().body("{\"error\":\"productId and actionType are required\"}");
            }

            if (!actionType.equals("VIEW") && !actionType.equals("CART") && !actionType.equals("PURCHASE")) {
                return ResponseEntity.badRequest().body("{\"error\":\"actionType must be VIEW, CART, or PURCHASE\"}");
            }

            behaviorRepo.record(userId, productId, actionType);
            log.info("行为记录 | userId={} | productId={} | actionType={}", userId, productId, actionType);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("行为记录失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body("{\"error\":\"invalid request body\"}");
        }
    }
}
