package com.ragagent.controller;

import com.ragagent.model.Product;
import com.ragagent.security.JwtAuthFilter;
import com.ragagent.service.ImageService;
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
    private final ImageService imageService;

    public RecommendationController(RecommendationService recommendationService, JwtAuthFilter jwtAuthFilter, ImageService imageService) {
        this.recommendationService = recommendationService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.imageService = imageService;
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
        imageService.fillImages(products);
        return ResponseEntity.ok(products);
    }
}
