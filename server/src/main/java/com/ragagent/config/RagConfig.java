package com.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    private int topK = 3;
    private double similarityThreshold = 0.5;
    private int maxContextMessages = 10;

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

    public int getMaxContextMessages() { return maxContextMessages; }
    public void setMaxContextMessages(int maxContextMessages) { this.maxContextMessages = maxContextMessages; }
}
