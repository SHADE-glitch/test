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
import java.util.function.Consumer;

/**
 * OpenAI Compatible Chat Completions 客户端，适配 DeepSeek / OpenAI / Ollama(v1) 等。
 */
@Slf4j
@Component
public class OpenAiCompatibleChatClient implements ChatClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleChatClient(AiProperties aiProperties, ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        JsonNode resp = post(request, false, null);
        if (resp == null) {
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 服务返回空响应");
        }

        JsonNode choice = resp.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText(null);
        String finishReason = choice.path("finish_reason").asText("stop");

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            for (JsonNode tc : tcArray) {
                toolCalls.add(new ToolCall(
                        tc.path("id").asText(),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText("{}")));
            }
        }
        return new ChatResponse(content, toolCalls, finishReason);
    }

    @Override
    public void chatStream(ChatRequest request, Consumer<String> onToken) {
        post(request, true, (body) -> {
            JsonNode choice = body.path("choices").path(0);
            JsonNode delta = choice.path("delta").path("content");
            if (delta.isTextual()) {
                String token = delta.asText();
                if (!token.isEmpty()) {
                    onToken.accept(token);
                }
            }
        });
    }

    private JsonNode post(ChatRequest request, boolean stream, Consumer<JsonNode> onChunk) {
        AiProperties.Provider provider = aiProperties.activeProvider();
        String url = provider.getBaseUrl() + "/chat/completions";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model());
        body.put("temperature", request.temperature() == null ? 0.7 : request.temperature());
        body.put("stream", stream);

        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : request.messages()) {
            messages.addObject().put("role", msg.role()).put("content", msg.content());
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ChatTool tool : request.tools()) {
                ObjectNode toolNode = tools.addObject();
                ObjectNode fn = toolNode.putObject("function");
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.set("parameters", tool.parameters());
            }
        }

        if (request.jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }

        RestClient client = restClientBuilder
                .baseUrl(url)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", stream ? "text/event-stream" : "application/json")
                .apply(c -> {
                    if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                        c.defaultHeaders(h -> h.setBearerAuth(provider.getApiKey()));
                    }
                })
                .build();

        try {
            if (stream) {
                client.post().body(body).exchange((req, resp) -> {
                    if (resp.getStatusCode() != HttpStatus.OK) {
                        throw new BizException(ResultCode.AI_SERVICE_ERROR,
                                "AI 服务返回 " + resp.getStatusCode());
                    }
                    try (InputStream is = resp.getBody();
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (!trimmed.startsWith("data:")) {
                                continue;
                            }
                            String data = trimmed.substring(5).trim();
                            if (data.isEmpty() || "[DONE]".equals(data)) {
                                continue;
                            }
                            try {
                                onChunk.accept(objectMapper.readTree(data));
                            } catch (Exception e) {
                                log.warn("skip invalid chunk: {}", e.getMessage());
                            }
                        }
                    }
                    return null;
                });
                return null;
            }
            return client.post().body(body).retrieve().body(JsonNode.class);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI chat request failed: {}", e.getMessage());
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 服务调用失败: " + e.getMessage());
        }
    }
}