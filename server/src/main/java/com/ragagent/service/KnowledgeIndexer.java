package com.ragagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ragagent.model.Product;
import com.ragagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@Component
@Order(1)
public class KnowledgeIndexer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);
    private static final String TEXT_COLLECTION = "products_text";
    private static final String IMAGE_COLLECTION = "products_image";

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;

    @Value("${chromadb.url}")
    private String chromaUrl;

    @Value("${chromadb.tenant:default_tenant}")
    private String tenant;

    @Value("${chromadb.database:default_database}")
    private String database;

    @Value("${dataset.path:../ecommerce_agent_dataset}")
    private String datasetPath;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    public KnowledgeIndexer(ProductRepository productRepository, EmbeddingService embeddingService) {
        this.productRepository = productRepository;
        this.embeddingService = embeddingService;
    }

    private String collectionsBase() {
        return chromaUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections";
    }

    @Override
    public void run(String... args) throws Exception {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            log.warn("MySQL 中无商品数据，跳过向量索引");
            return;
        }

        // 检查是否已有数据
        String existingId = getCollectionId(TEXT_COLLECTION);
        if (existingId != null && collectionHasData(existingId)) {
            log.info("ChromaDB {} 已有数据，跳过索引（缓存命中，零 API 调用）", TEXT_COLLECTION);
            return;
        }

        log.info("开始向量索引，共 {} 条商品（首次运行，需调用 Embedding API）...", products.size());

        // 创建 collection 并获取 ID
        String textColId = createCollection(TEXT_COLLECTION);
        String imageColId = createCollection(IMAGE_COLLECTION);

        if (textColId == null) {
            log.error("创建 Collection 失败，终止索引");
            return;
        }

        int textCount = 0, imageCount = 0;
        int failCount = 0;

        for (Product product : products) {
            String productId = product.getProductId();

            try {
                String text = String.format("%s %s %s %s %s",
                        product.getTitle(),
                        product.getBrand(),
                        product.getCategory(),
                        product.getSubCategory(),
                        product.getMarketingDescription() != null ? product.getMarketingDescription() : "");

                float[] textVector = embeddingService.embedText(text);
                addToCollection(textColId, productId, textVector, product);
                textCount++;

                String imagePath = resolveImagePath(product.getImagePath());
                if (imagePath != null && imageColId != null && Files.exists(Path.of(imagePath))) {
                    float[] imageVector = embeddingService.embedImage(imagePath);
                    addToCollection(imageColId, productId, imageVector, product);
                    imageCount++;
                }

                failCount = 0;

                if ((textCount + imageCount) % 20 == 0) {
                    log.info("索引进度: 文本 {}/{}, 图片 {}/{}", textCount, products.size(), imageCount, products.size());
                }

            } catch (Exception e) {
                failCount++;
                log.error("索引失败: {} - {}", productId, e.getMessage());
                if (failCount >= 3) {
                    log.error("连续失败 {} 次，终止索引", failCount);
                    return;
                }
            }
        }

        log.info("向量索引完成! 文本 {} 条, 图片 {} 条（后续启动将跳过，零 API 调用）", textCount, imageCount);
    }

    private String resolveImagePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        Path base = Paths.get(datasetPath).toAbsolutePath().normalize();
        return base.resolve(relativePath).toString();
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

    private boolean collectionHasData(String collectionId) {
        try {
            // 查询一条数据来判断是否有数据
            JsonObject body = new JsonObject();
            JsonArray queryEmbeddings = new JsonArray();
            JsonArray dummyEmb = new JsonArray();
            for (int i = 0; i < 2048; i++) dummyEmb.add(0.1f);
            queryEmbeddings.add(dummyEmb);
            body.add("query_embeddings", queryEmbeddings);
            body.addProperty("n_results", 1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectionsBase() + "/" + collectionId + "/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                JsonArray ids = resp.getAsJsonArray("ids");
                return ids.size() > 0 && ids.get(0).getAsJsonArray().size() > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String createCollection(String name) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            JsonObject metadata = new JsonObject();
            metadata.addProperty("hnsw:space", "cosine");
            body.add("metadata", metadata);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectionsBase()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 409) {
                // 200 = 创建成功, 409 = 已存在
                if (response.statusCode() == 200) {
                    JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                    String id = resp.get("id").getAsString();
                    log.info("创建 Collection '{}': id={}", name, id);
                    return id;
                } else {
                    // 409: 已存在，获取现有 ID
                    String existingId = getCollectionId(name);
                    log.info("Collection '{}' 已存在: id={}", name, existingId);
                    return existingId;
                }
            }
            log.warn("创建 Collection '{}' 失败: status={}", name, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("创建 Collection 失败: {}", name, e);
            return null;
        }
    }

    private void addToCollection(String collectionId, String id, float[] embedding, Product product) {
        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("product_id", product.getProductId());
            metadata.addProperty("title", product.getTitle());
            metadata.addProperty("brand", product.getBrand());
            metadata.addProperty("category", product.getCategory());

            JsonArray ids = new JsonArray();
            ids.add(id);

            JsonArray embeddings = new JsonArray();
            JsonArray embArray = new JsonArray();
            for (float v : embedding) embArray.add(v);
            embeddings.add(embArray);

            JsonArray metadatas = new JsonArray();
            metadatas.add(metadata);

            JsonObject body = new JsonObject();
            body.add("ids", ids);
            body.add("embeddings", embeddings);
            body.add("metadatas", metadatas);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectionsBase() + "/" + collectionId + "/add"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                log.warn("写入失败: {} / {} / status={}", collectionId, id, resp.statusCode());
            }

        } catch (Exception e) {
            log.error("写入 Collection 失败: {} / {}", collectionId, id, e);
        }
    }
}
