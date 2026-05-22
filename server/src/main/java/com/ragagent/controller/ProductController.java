package com.ragagent.controller;

import com.ragagent.model.Product;
import com.ragagent.repository.ProductRepository;
import com.ragagent.service.RetrieverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final RetrieverService retrieverService;

    public ProductController(ProductRepository productRepository, RetrieverService retrieverService) {
        this.productRepository = productRepository;
        this.retrieverService = retrieverService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        Product product = productRepository.findById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    @GetMapping("/batch")
    public ResponseEntity<List<Product>> getProducts(@RequestParam String ids) {
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        List<Product> products = productRepository.findByIds(idList);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(required = false) String category) {
        List<String> productIds = retrieverService.retrieveByText(query, topK, category);
        List<Product> products = productRepository.findByIds(productIds);
        return ResponseEntity.ok(products);
    }
}
