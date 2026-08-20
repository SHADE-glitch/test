package com.aiview.interview.service;

import com.aiview.agent.ai.ChatClient;
import com.aiview.agent.ai.ChatMessage;
import com.aiview.agent.ai.ChatRequest;
import com.aiview.agent.ai.ChatResponse;
import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.aiview.config.AiProperties;
import com.aiview.interview.entity.InterviewMessage;
import com.aiview.interview.entity.InterviewResult;
import com.aiview.interview.entity.InterviewSession;
import com.aiview.interview.mapper.InterviewMessageMapper;
import com.aiview.interview.mapper.InterviewResultMapper;
import com.aiview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewScoringService {

    public static final int DIMENSION_MAX_SCORE = 10;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewResultMapper resultMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public record ScoringMessage(Long sessionId) {
    }

    @RabbitListener(queues = "aiview.interview.scoring")
    public void score(ScoringMessage message) {
        Long sessionId = message.sessionId();
        log.info("开始评分 session={}", sessionId);
        try {
            InterviewSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                log.warn("评分目标 session {} 不存在，跳过", sessionId);
                return;
            }
            List<InterviewMessage> rows = messageMapper.selectList(
                    new LambdaQueryWrapper<InterviewMessage>()
                            .eq(InterviewMessage::getSessionId, sessionId)
                            .orderByAsc(InterviewMessage::getId));
            List<ChatMessage> history = rows.stream()
                    .filter(m -> !"feedback".equals(m.getKind()))
                    .map(m -> new ChatMessage("AI".equals(m.getRole()) ? "assistant" : "user", m.getContent()))
                    .toList();

            ChatResponse response = chatClient.chat(ChatRequest.builder(aiProperties.activeProvider().getChatModel())
                    .messages(buildScoringMessages(session, history))
                    .temperature(0.3)
                    .jsonMode(true)
                    .build());

            JsonNode root = objectMapper.readTree(response.content());
            JsonNode dims = root.path("dimensions");
            if (!dims.isObject() || dims.isEmpty()) {
                throw new BizException(ResultCode.AI_SERVICE_ERROR, "评分结果缺少 dimensions");
            }
            ObjectNode normalized = objectMapper.createObjectNode();
            int[] sum = {0};
            dims.fields().forEachRemaining(e -> {
                ObjectNode d = objectMapper.createObjectNode();
                if (e.getValue().isNumber()) {
                    d.put("score", e.getValue().asInt());
                    d.put("comment", "");
                } else if (e.getValue().isObject()) {
                    d.put("score", e.getValue().path("score").asInt());
                    d.put("comment", e.getValue().path("comment").asText(""));
                } else {
                    d.put("score", 0);
                    d.put("comment", "");
                }
                sum[0] += d.get("score").asInt();
                normalized.set(e.getKey(), d);
            });
            int total = root.path("totalScore").asInt();
            if (total <= 0) {
                total = sum[0];
            }

            InterviewResult result = new InterviewResult();
            result.setSessionId(sessionId);
            result.setUserId(session.getUserId());
            result.setDimensions(objectMapper.writeValueAsString(normalized));
            result.setTotalScore(total);
            result.setWeakPoints(toStringArray(root.get("weakPoints")));
            result.setSuggestions(toStringArray(root.get("suggestions")));
            resultMapper.insert(result);

            session.setTotalScore(total);
            session.setReport(root.toString());
            sessionMapper.updateById(session);
            log.info("评分完成 session={} totalScore={}", sessionId, total);
        } catch (Exception e) {
            log.error("评分失败 session={}", sessionId, e);
            throw new RuntimeException(e);
        }
    }

    private List<ChatMessage> buildScoringMessages(InterviewSession session, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildScoringPrompt(session, history.size() / 2)));
        messages.addAll(history);
        return messages;
    }

    private String buildScoringPrompt(InterviewSession session, int questionCount) {
        return """
                你是一名资深 %s 技术面试官，请根据以下面试问答记录（共 %d 问）对候选人进行四维评分。
                四个维度（每个维度 0-10 分）：
                1. knowledge 知识掌握：对原理的理解深度与准确性；
                2. expression 表达清晰：回答的条理性、逻辑性与专业术语使用；
                3. source 实践来源：回答是否结合实际项目经验、踩坑与调优；
                4. analysis 问题分析：面对追问与挖坑时的分析深度、应变与延伸能力。
                只输出一个 JSON 对象，不要输出任何其他内容，格式：
                {"dimensions":{"knowledge":{"score":0,"comment":"一句话点评"},"expression":{...},"source":{...},"analysis":{...}},"totalScore":0,"weakPoints":["薄弱点1"],"suggestions":["建议1"]}
                totalScore 为四维分数之和（0-40），comment 控制在 20 字以内，weakPoints 与 suggestions 各 2-3 条。
                """.formatted(session.getTopic(), questionCount);
    }

    private String toStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return "[]";
        }
        List<String> list = new ArrayList<>();
        node.forEach(n -> list.add(n.asText()));
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}