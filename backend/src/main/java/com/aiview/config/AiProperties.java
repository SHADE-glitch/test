package com.aiview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String active = "deepseek";

    private Map<String, Provider> providers = new HashMap<>();

    public Provider activeProvider() {
        Provider provider = providers.get(active);
        if (provider == null) {
            throw new IllegalStateException("AI provider not configured: " + active);
        }
        return provider;
    }

    @Data
    public static class Provider {
        private String baseUrl;
        private String apiKey = "";
        private String chatModel;
        private String embeddingModel = "";
    }
}