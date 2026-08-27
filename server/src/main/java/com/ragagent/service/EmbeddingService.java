package com.ragagent.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;



/**
 * 本地 Embedding 服务客户端
 *
 * <p>调用本地 Python 服务（FastAPI + Chinese-CLIP），
 * 替代原火山引擎 doubao-embedding-vision 多模态 embedding。
 *
 * <p>API 设计见 docs/Day16-本地多模态Embedding服务.md §5.1
 *   - POST /embed/text     {"text":"..."}       → {"vector":[...], "dim":N}
 *   - POST /embed/image    {"image_base64":"...","mime_type":"jpeg"} → {"vector":[...], "dim":N}
 *   - POST /embed/batch    {"texts":["a","b"]}  → {"vectors":[[...],[...]], "dim":N}
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // 强制 HTTP/1.1：uvicorn 不支持 h2c upgrade
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    /** 文本向量化 */
    public float[] embedText(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);

        return parseSingleVector(send(buildPost("/embed/text", body.toString())));
    }

    /** 批量文本向量化（用于索引阶段加速） */
    public List<float[]> embedBatch(List<String> texts) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String t : texts) arr.add(t);
        body.add("texts", arr);

        // 批量任务放宽超时到 120s
        HttpRequest.Builder builder = buildPost("/embed/batch", body.toString())
                .timeout(Duration.ofSeconds(120));

        String respBody = send(builder);
        try {
            JsonObject resp = gson.fromJson(respBody, JsonObject.class);
            JsonArray vectors = resp.getAsJsonArray("vectors");
            List<float[]> result = new ArrayList<>(vectors.size());
            for (int i = 0; i < vectors.size(); i++) {
                JsonArray vecArr = vectors.get(i).getAsJsonArray();
                float[] vec = new float[vecArr.size()];
                for (int j = 0; j < vec.length; j++) {
                    vec[j] = vecArr.get(j).getAsFloat();
                }
                result.add(vec);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Embedding /embed/batch 响应解析失败: " + e.getMessage(), e);
        }
    }

    /** 图片向量化（base64 上传） */
    public float[] embedImage(String imagePath) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(imagePath));
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = imagePath.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

            JsonObject body = new JsonObject();
            body.addProperty("image_base64", base64);
            body.addProperty("mime_type", mime);

            return parseSingleVector(send(buildPost("/embed/image", body.toString())));
        } catch (IOException e) {
            log.error("读取图片失败: {}", imagePath, e);
            throw new RuntimeException("图片读取失败: " + imagePath, e);
        }
    }

    // ==================== 内部工具 ====================

    private HttpRequest.Builder buildPost(String path, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)   // 本地服务不校验，保留兼容
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30));
    }

    private String send(HttpRequest.Builder requestBuilder) {
        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API 返回 {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Embedding API 失败, status=" + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            log.error("Embedding API 网络异常: {}", e.getMessage());
            throw new RuntimeException("Embedding API 通信失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding API 被中断", e);
        }
    }

    private float[] parseSingleVector(String respBody) {
        JsonObject resp = gson.fromJson(respBody, JsonObject.class);
        JsonArray vecArr = resp.getAsJsonArray("vector");
        float[] result = new float[vecArr.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = vecArr.get(i).getAsFloat();
        }
        return result;
    }
}