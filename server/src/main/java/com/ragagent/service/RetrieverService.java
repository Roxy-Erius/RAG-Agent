package com.ragagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ragagent.config.RagConfig;
import com.ragagent.model.Product;
import com.ragagent.model.ProductSearchResult;
import com.ragagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetrieverService {

    private static final Logger log = LoggerFactory.getLogger(RetrieverService.class);
    private static final String TEXT_COLLECTION = "products_text";
    private static final String IMAGE_COLLECTION = "products_image";
    private static final double TEXT_WEIGHT = 0.7;
    private static final double IMAGE_WEIGHT = 0.3;
    private static final int RRF_K = 60;
    // 多模态融合权重：text→text : text→image（CLIP 跨模态）
    // 评测调优结论：图像通道权重更高效果更好（CLIP 图像 embedding 区分度 > 文本塔）
    private static final double MM_TEXT_WEIGHT = 0.3;
    private static final double MM_IMAGE_WEIGHT = 0.7;
    private static final int MM_POOL_MULT = 4;

    private final RagConfig ragConfig;
    private final EmbeddingService embeddingService;
    private final ProductRepository productRepository;

    @Value("${chromadb.url}")
    private String chromaUrl;

    @Value("${chromadb.tenant:default_tenant}")
    private String tenant;

    @Value("${chromadb.database:default_database}")
    private String database;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    // 缓存 collection ID
    private String textCollectionId;
    private String imageCollectionId;

    public RetrieverService(RagConfig ragConfig, EmbeddingService embeddingService, ProductRepository productRepository) {
        this.ragConfig = ragConfig;
        this.embeddingService = embeddingService;
        this.productRepository = productRepository;
    }

    private String collectionsBase() {
        return chromaUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections";
    }

    private String getCollectionId(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectionsBase() + "/" + name))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject col = gson.fromJson(response.body(), JsonObject.class);
                return col.get("id").getAsString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTextCollectionId() {
        if (textCollectionId == null) {
            textCollectionId = getCollectionId(TEXT_COLLECTION);
        }
        return textCollectionId;
    }

    private String getImageCollectionId() {
        if (imageCollectionId == null) {
            imageCollectionId = getCollectionId(IMAGE_COLLECTION);
        }
        return imageCollectionId;
    }

    public List<String> okretrieveByText(String query, int topK) {
        return retrieveByText(query, topK, null);
    }

    public List<String> retrieveByText(String query, int topK, String category) {
        float[] queryVector = embeddingService.embedText(query);

        JsonObject whereFilter = null;
        if (category != null && !category.isEmpty()) {
            JsonObject categoryEq = new JsonObject();
            categoryEq.addProperty("$eq", category);
            whereFilter = new JsonObject();
            whereFilter.add("category", categoryEq);
        }

        List<ScoredResult> results = queryCollection(getTextCollectionId(), queryVector, topK * 2, whereFilter);

        double threshold = ragConfig.getSimilarityThreshold();
        return results.stream()
                .filter(r -> r.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredResult::getScore).reversed())
                .limit(topK)
                .map(r -> r.productId)
                .toList();
    }

    /**
     * 文本检索 + DB 联查，返回带完整商品信息的结果（供 ChatService 使用）。
     */
    public List<ProductSearchResult> retrieveProductsByText(String query, int topK, String category) {
        log.debug("向量检索: query=\"{}\" topK={} category={}", query, topK, category);
        float[] queryVector = embeddingService.embedText(query);

        JsonObject whereFilter = null;
        if (category != null && !category.isEmpty()) {
            JsonObject categoryEq = new JsonObject();
            categoryEq.addProperty("$eq", category);
            whereFilter = new JsonObject();
            whereFilter.add("category", categoryEq);
        }

        List<ScoredResult> results = queryCollection(getTextCollectionId(), queryVector, topK * 2, whereFilter);

        double threshold = ragConfig.getSimilarityThreshold();
        List<ScoredResult> topResults = results.stream()
                .filter(r -> r.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredResult::getScore).reversed())
                .limit(topK)
                .toList();

        log.debug("向量检索结果: raw={} filtered={} threshold={}",
                results.size(), topResults.size(), threshold);
        for (ScoredResult r : topResults) {
            log.debug("  {} | score={:.4f}", r.productId, r.score);
        }

        List<String> productIds = topResults.stream().map(r -> r.productId).toList();
        List<Product> products = productRepository.findByIds(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        return topResults.stream()
                .map(r -> {
                    Product p = productMap.get(r.productId);
                    if (p == null) return null;
                    return new ProductSearchResult(
                            p.getProductId(), r.score, p.getTitle(), p.getBrand(),
                            p.getCategory(), p.getSubCategory(),
                            p.getBasePrice().doubleValue(),
                            p.getImagePath(), p.getMarketingDescription());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 多模态融合检索：text→text（CLIP 文本塔） + text→image（CLIP 跨模态，用文本编码查询图像 collection）。
     * 用 RRF 融合两路结果。这才是让图文对齐模型发挥作用的用法。
     */
    public List<ProductSearchResult> retrieveProductsMultiModal(String query, int topK, String category) {
        return retrieveProductsMultiModal(query, topK, category, MM_TEXT_WEIGHT, MM_IMAGE_WEIGHT, MM_POOL_MULT);
    }

    /**
     * 可调参版本（供调参与评测）：text→text 与 text→image 两路 RRF 融合。
     *
     * @param textWeight  文本通道权重
     * @param imageWeight 跨模态（text→image）通道权重
     * @param poolMult    每路候选池倍数（取 topK * poolMult 条）
     */
    public List<ProductSearchResult> retrieveProductsMultiModal(String query, int topK, String category,
                                                                double textWeight, double imageWeight, int poolMult) {
        log.debug("多模态检索: query=\"{}\" topK={} category={} w=({}, {}) pool={}x",
                query, topK, category, textWeight, imageWeight, poolMult);
        float[] queryVector = embeddingService.embedText(query);
        JsonObject whereFilter = buildCategoryFilter(category);
        int pool = topK * Math.max(1, poolMult);

        // 通道1：文本空间 text→text
        List<ScoredResult> textResults = queryCollection(getTextCollectionId(), queryVector, pool, whereFilter);
        // 通道2：跨模态 text→image（同一个 CLIP 文本向量，查询图像 collection）
        List<ScoredResult> imageResults = queryCollection(getImageCollectionId(), queryVector, pool, whereFilter);

        Map<String, Double> rrf = new HashMap<>();
        accumulateRrf(rrf, textResults, textWeight);
        accumulateRrf(rrf, imageResults, imageWeight);

        List<String> productIds = rrf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        log.debug("多模态结果: text通道={} image通道={} fused={}",
                textResults.size(), imageResults.size(), productIds.size());
        return toProductResults(productIds, rrf);
    }

    /** 构造 category 过滤条件 */
    private JsonObject buildCategoryFilter(String category) {
        if (category == null || category.isEmpty()) return null;
        JsonObject eq = new JsonObject();
        eq.addProperty("$eq", category);
        JsonObject where = new JsonObject();
        where.add("category", eq);
        return where;
    }

    /** RRF 累加：排名第 i 的贡献 weight/(K+i+1) */
    private void accumulateRrf(Map<String, Double> rrf, List<ScoredResult> results, double weight) {
        for (int i = 0; i < results.size(); i++) {
            rrf.merge(results.get(i).productId, weight / (RRF_K + i + 1), Double::sum);
        }
    }

    /** 按有序 productId 列表联查 DB，组装成 ProductSearchResult（分数取 scoreMap） */
    private List<ProductSearchResult> toProductResults(List<String> productIds, Map<String, Double> scoreMap) {
        if (productIds.isEmpty()) return List.of();
        List<Product> products = productRepository.findByIds(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        return productIds.stream()
                .map(id -> {
                    Product p = productMap.get(id);
                    if (p == null) return null;
                    double score = scoreMap != null ? scoreMap.getOrDefault(id, 0.0) : 0.0;
                    return new ProductSearchResult(
                            p.getProductId(), score, p.getTitle(), p.getBrand(),
                            p.getCategory(), p.getSubCategory(),
                            p.getBasePrice().doubleValue(),
                            p.getImagePath(), p.getMarketingDescription());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public List<String> retrieveByImage(String imagePath, int topK) {
        float[] queryVector = embeddingService.embedImage(imagePath);
        List<ScoredResult> results = queryCollection(getImageCollectionId(), queryVector, topK * 2);

        double threshold = ragConfig.getSimilarityThreshold();
        return results.stream()
                .filter(r -> r.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredResult::getScore).reversed())
                .limit(topK)
                .map(r -> r.productId)
                .toList();
    }

    public List<String> retrieveHybrid(String textQuery, String imagePath, int topK) {
        float[] textVector = embeddingService.embedText(textQuery);
        float[] imageVector = embeddingService.embedImage(imagePath);

        List<ScoredResult> textResults = queryCollection(getTextCollectionId(), textVector, topK * 2);
        List<ScoredResult> imageResults = queryCollection(getImageCollectionId(), imageVector, topK * 2);

        Map<String, Double> rrfScores = new HashMap<>();

        for (int i = 0; i < textResults.size(); i++) {
            String pid = textResults.get(i).productId;
            rrfScores.merge(pid, TEXT_WEIGHT / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < imageResults.size(); i++) {
            String pid = imageResults.get(i).productId;
            rrfScores.merge(pid, IMAGE_WEIGHT / (RRF_K + i + 1), Double::sum);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<ScoredResult> queryCollection(String collectionId, float[] queryVector, int nResults) {
        return queryCollection(collectionId, queryVector, nResults, null);
    }

    private List<ScoredResult> queryCollection(String collectionId, float[] queryVector, int nResults, JsonObject whereFilter) {
        if (collectionId == null) {
            log.warn("Collection ID 为空，跳过查询");
            return List.of();
        }

        try {
            JsonArray queryEmbeddings = new JsonArray();
            JsonArray embArray = new JsonArray();
            for (float v : queryVector) embArray.add(v);
            queryEmbeddings.add(embArray);

            JsonObject body = new JsonObject();
            body.add("query_embeddings", queryEmbeddings);
            body.addProperty("n_results", nResults);

            if (whereFilter != null) {
                body.add("where", whereFilter);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectionsBase() + "/" + collectionId + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("查询 Collection '{}' 失败: status={}", collectionId, response.statusCode());
                return List.of();
            }

            JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
            JsonArray ids = resp.getAsJsonArray("ids").get(0).getAsJsonArray();
            JsonArray distances = resp.getAsJsonArray("distances").get(0).getAsJsonArray();

            List<ScoredResult> results = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i).getAsString();
                double distance = distances.get(i).getAsDouble();
                double score = 1.0 - distance;
                results.add(new ScoredResult(id, score));
            }
            return results;

        } catch (Exception e) {
            log.error("查询 Collection '{}' 异常: {}", collectionId, e.getMessage());
            return List.of();
        }
    }

    static class ScoredResult {
        final String productId;
        final double score;

        ScoredResult(String productId, double score) {
            this.productId = productId;
            this.score = score;
        }

        double getScore() { return score; }
    }
}
