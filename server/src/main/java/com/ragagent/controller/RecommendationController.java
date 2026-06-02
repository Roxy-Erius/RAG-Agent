package com.ragagent.controller;

import com.ragagent.model.Product;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RecommendationController {
    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);
    private final RecommendationService recommendationService;
    private final JwtAuthFilter jwtAuthFilter;

    public RecommendationController(RecommendationService recommendationService, JwtAuthFilter jwtAuthFilter) {
        this.recommendationService = recommendationService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(HttpServletRequest request) {
        Long userId = jwtAuthFilter.getUserId(request);
        if (userId == null) {
            // Not logged in — return empty
            return ResponseEntity.ok(List.of());
        }
        log.info("==> GET /api/recommendations | userId={}", userId);
        List<Product> products = recommendationService.recommend(userId);
        return ResponseEntity.ok(products);
    }
}
