package com.ragagent.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    // Note: volcengine.base-url 已废弃，chat 走 chat.base-url，embedding 走 embedding.base-url（重构见 Day16 PRD）

    @Value("${volcengine.chat.api-key}")
    private String apiKey;

    @Value("${volcengine.chat.model-id}")
    private String modelId;

    @Value("${volcengine.chat.base-url}")
    private String chatBaseUrl;

    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(chatBaseUrl)
                .apiKey(apiKey)
                .modelName(modelId)
                .temperature(0.7)
                .maxTokens(1024)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
