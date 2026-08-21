package com.aiview.agent.ai;

import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.aiview.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Compatible Embeddings 客户端，适配 OpenAI / Ollama(v1) 等。
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleEmbeddingClient(AiProperties aiProperties, ObjectMapper objectMapper,
                                           RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public float[] embed(String text) {
        return embedInternal(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return embedInternal(texts);
    }

    private List<float[]> embedInternal(List<String> texts) {
        AiProperties.Provider provider = aiProperties.activeProvider();
        String model = provider.getEmbeddingModel();
        if (model == null || model.isBlank()) {
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "当前 AI 供应商未配置 embedding 模型");
        }
        String url = provider.getBaseUrl() + "/embeddings";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode inputs = body.putArray("input");
        texts.forEach(inputs::add);

        RestClient client = restClientBuilder
                .baseUrl(url)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .apply(c -> {
                    if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                        c.defaultHeaders(h -> h.setBearerAuth(provider.getApiKey()));
                    }
                })
                .build();

        try {
            JsonNode resp = client.post().body(body).exchange((req, res) -> {
                if (res.getStatusCode() != HttpStatus.OK) {
                    throw new BizException(ResultCode.AI_SERVICE_ERROR,
                            "Embedding 服务返回 " + res.getStatusCode());
                }
                try (InputStream is = res.getBody();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    return objectMapper.readTree(reader);
                }
            });
            List<float[]> result = new ArrayList<>();
            JsonNode data = resp.path("data");
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                result.add(vec);
            }
            if (result.isEmpty()) {
                throw new BizException(ResultCode.AI_SERVICE_ERROR, "Embedding 服务返回空数据");
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("embedding request failed: {}", e.getMessage());
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "Embedding 服务调用失败: " + e.getMessage());
        }
    }
}