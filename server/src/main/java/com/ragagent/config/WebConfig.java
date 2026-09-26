package com.ragagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ragagent.security.AdminAuthInterceptor;

import java.nio.file.Paths;

/**
 * 静态资源配置 — 让商品图片可通过 HTTP 访问。
 * 访问路径：http://localhost:8080/images/1_美妆护肤/images/p_beauty_001_live.jpg
 *
 * CORS 配置 — 放行 Vite dev（localhost:5173）+ 生产域名。
 * 拦截器 — /api/admin/** 需 ADMIN 角色。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${dataset.path:../ecommerce_agent_dataset}")
    private String datasetPath;

    @Value("${web.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
    private String allowedOrigins;

    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 /images/** 映射到数据集目录下的图片文件
        String absolutePath = Paths.get(datasetPath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(absolutePath);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
