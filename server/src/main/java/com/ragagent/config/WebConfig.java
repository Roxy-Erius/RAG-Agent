package com.ragagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源配置 — 让商品图片可通过 HTTP 访问。
 * 访问路径：http://localhost:8080/images/1_美妆护肤/images/p_beauty_001_live.jpg
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${dataset.path:../ecommerce_agent_dataset}")
    private String datasetPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 /images/** 映射到数据集目录下的图片文件
        String absolutePath = Paths.get(datasetPath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(absolutePath);
    }
}
