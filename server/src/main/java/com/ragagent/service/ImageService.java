package com.ragagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 图片服务 — 读取商品图片并转为 Base64，注入到 API 响应中。
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    @Value("${dataset.path:../ecommerce_agent_dataset}")
    private String datasetPath;

    /**
     * 读取商品图片并返回 data:image/jpeg;base64,... 格式字符串。
     * @param imagePath 数据库中的相对路径，如 "1_美妆护肤/images/p_beauty_001_live.jpg"
     * @return Base64 data URL，图片不存在时返回 null
     */
    public String getImageBase64(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;
        try {
            Path fullPath = Paths.get(datasetPath, imagePath);
            if (!Files.exists(fullPath)) {
                log.debug("图片不存在: {}", fullPath);
                return null;
            }
            byte[] bytes = Files.readAllBytes(fullPath);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = imagePath.endsWith(".png") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + base64;
        } catch (IOException e) {
            log.warn("读取图片失败: {}", imagePath, e.getMessage());
            return null;
        }
    }

    /**
     * 批量填充商品的 imageBase64 字段。
     */
    public void fillImages(java.util.List<com.ragagent.model.Product> products) {
        for (com.ragagent.model.Product p : products) {
            p.setImageBase64(getImageBase64(p.getImagePath()));
        }
    }
}
