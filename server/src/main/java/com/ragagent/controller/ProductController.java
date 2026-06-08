package com.ragagent.controller;

import com.ragagent.model.Product;
import com.ragagent.repository.ProductRepository;
import com.ragagent.service.ImageService;
import com.ragagent.service.RetrieverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductRepository productRepository;
    private final RetrieverService retrieverService;
    private final ImageService imageService;

    public ProductController(ProductRepository productRepository, RetrieverService retrieverService, ImageService imageService) {
        this.productRepository = productRepository;
        this.retrieverService = retrieverService;
        this.imageService = imageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        log.debug("GET /api/products/{}", id);
        Product product = productRepository.findById(id);
        if (product == null) {
            log.warn("商品不存在: {}", id);
            return ResponseEntity.notFound().build();
        }
        imageService.fillImages(List.of(product));
        return ResponseEntity.ok(product);
    }

    @GetMapping("/batch")
    public ResponseEntity<List<Product>> getProducts(@RequestParam String ids) {
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        log.debug("GET /api/products/batch | ids={} | count={}", ids, idList.size());
        List<Product> products = productRepository.findByIds(idList);
        imageService.fillImages(products);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(required = false) String category) {
        log.info("==> 语义搜索 | query=\"{}\" | topK={} | category={}", query, topK, category);
        List<String> productIds = retrieverService.retrieveByText(query, topK, category);
        log.info("<== 语义搜索结果 | query=\"{}\" | found={}", query, productIds.size());
        List<Product> products = productRepository.findByIds(productIds);
        imageService.fillImages(products);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}/skus")
    public ResponseEntity<List<Map<String, Object>>> getSkus(@PathVariable String id) {
        log.debug("GET /api/products/{}/skus", id);
        return ResponseEntity.ok(productRepository.findSkusByProductId(id));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<Map<String, Object>>> getReviews(@PathVariable String id) {
        log.debug("GET /api/products/{}/reviews", id);
        return ResponseEntity.ok(productRepository.findReviewsByProductId(id));
    }

    @GetMapping("/{id}/faqs")
    public ResponseEntity<List<Map<String, Object>>> getFaqs(@PathVariable String id) {
        log.debug("GET /api/products/{}/faqs", id);
        return ResponseEntity.ok(productRepository.findFaqsByProductId(id));
    }
}
