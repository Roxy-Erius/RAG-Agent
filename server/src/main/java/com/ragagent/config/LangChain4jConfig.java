package com.ragagent.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${volcengine.base-url}")
    private String baseUrl;

    @Value("${volcengine.chat.api-key}")
    private String apiKey;

    @Value("${volcengine.chat.model-id}")
    private String modelId;

    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelId)
                .temperature(0.7)
                .maxTokens(1024)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
