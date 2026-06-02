package com.ragagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ragAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG 电商导购 Agent API")
                        .description("基于 RAG 的多模态电商智能导购助手后端接口")
                        .version("1.0.0"));
    }
}
