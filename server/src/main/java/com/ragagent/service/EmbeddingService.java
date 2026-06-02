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
import java.util.Base64;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int MAX_RETRIES = 3;

    @Value("${volcengine.base-url}")
    private String baseUrl;

    @Value("${volcengine.embedding.api-key}")
    private String apiKey;

    @Value("${volcengine.embedding.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    public float[] embedText(String text) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);

        JsonArray input = new JsonArray();
        input.add(item);

        return callApi(input);
    }

    public float[] embedImage(String imagePath) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(imagePath));
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = imagePath.endsWith(".png") ? "image/png" : "image/jpeg";

            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", "data:" + mime + ";base64," + base64);

            JsonObject item = new JsonObject();
            item.addProperty("type", "image_url");
            item.add("image_url", imageUrl);

            JsonArray input = new JsonArray();
            input.add(item);

            return callApi(input);
        } catch (IOException e) {
            log.error("读取图片失败: {}", imagePath, e);
            throw new RuntimeException("图片读取失败: " + imagePath, e);
        }
    }

    private float[] callApi(JsonArray input) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("input", input);

        String json = gson.toJson(body);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/embeddings/multimodal"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(60))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("Embedding API 返回 {}: {}", response.statusCode(), response.body());
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(1000L * attempt);
                        continue;
                    }
                    throw new RuntimeException("Embedding API 失败, status=" + response.statusCode());
                }

                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                JsonArray embArray = resp.getAsJsonObject("data")
                        .getAsJsonArray("embedding");

                float[] result = new float[embArray.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = embArray.get(i).getAsFloat();
                }
                return result;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Embedding API 被中断", e);
            } catch (IOException e) {
                log.warn("Embedding API 异常, 第 {} 次: {}", attempt, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Embedding API 失败，已重试 " + MAX_RETRIES + " 次", e);
                }
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        throw new RuntimeException("Embedding API 失败");
    }
}
