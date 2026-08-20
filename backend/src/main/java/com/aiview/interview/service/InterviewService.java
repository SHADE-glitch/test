package com.aiview.interview.service;

import com.aiview.agent.ai.ChatClient;
import com.aiview.agent.ai.ChatMessage;
import com.aiview.agent.ai.ChatRequest;
import com.aiview.agent.ai.ChatResponse;
import com.aiview.agent.ai.ChatTool;
import com.aiview.agent.ai.ToolCall;
import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.aiview.config.AiProperties;
import com.aiview.config.RabbitConfig;
import com.aiview.interview.dto.CreateInterviewRequest;
import com.aiview.interview.dto.InterviewResultVO;
import com.aiview.interview.dto.InterviewSessionVO;
import com.aiview.interview.dto.MessageVO;
import com.aiview.interview.dto.TopicVO;
import com.aiview.interview.entity.InterviewMessage;
import com.aiview.interview.entity.InterviewResult;
import com.aiview.interview.entity.InterviewSession;
import com.aiview.interview.entity.KnowledgePoint;
import com.aiview.interview.mapper.InterviewMessageMapper;
import com.aiview.interview.mapper.InterviewResultMapper;
import com.aiview.interview.mapper.InterviewSessionMapper;
import com.aiview.interview.mapper.KnowledgePointMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewResultMapper resultMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final RedissonClient redissonClient;
    private final InterviewStateStore stateStore;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final long LOCK_WAIT_SECONDS = 5;

    private static final int MAX_QUESTIONS = 6;

    private record AiDecision(boolean finished, String text) {
    }

    public List<TopicVO> listTopics() {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().orderByAsc(KnowledgePoint::getSortOrder));
        Map<String, List<TopicVO.KnowledgePointVO>> grouped = new LinkedHashMap<>();
        for (KnowledgePoint p : points) {
            grouped.computeIfAbsent(p.getTopic(), k -> new ArrayList<>())
                    .add(new TopicVO.KnowledgePointVO(p.getId(), p.getName(), p.getDifficulty()));
        }
        return grouped.entrySet().stream()
                .map(e -> new TopicVO(e.getKey(), e.getValue()))
                .toList();
    }

    public InterviewSessionVO create(Long userId, CreateInterviewRequest req) {
        if (!topicExists(req.getTopic())) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的面试主题: " + req.getTopic());
        }
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setTopic(req.getTopic());
        session.setLevel(req.getLevel());
        session.setStatus(InterviewStatus.START.name());
        session.setQuestionCount(0);
        sessionMapper.insert(session);

        RLock lock = lockFor(session.getId());
        try {
            stateStore.write(session.getId(), new InterviewStateStore.SessionState(
                    InterviewStatus.START.name(), 0, null));

            List<ChatMessage> messages = decisionMessages(session, List.of());
            AiDecision d = runDecision(messages, false, null);

            if (d.finished()) {
                InterviewMessage msg = saveAiMessage(session.getId(), "feedback", d.text(), null);
                finishSession(session, msg);
                return toVO(session, List.of(toMessageVO(msg)), msg.getId());
            }

            InterviewMessage msg = saveAiMessage(session.getId(), "question", d.text(), null);
            session.setStatus(InterviewStatus.ASKING.name());
            session.setCurrentQuestionId(msg.getId());
            session.setQuestionCount(1);
            sessionMapper.updateById(session);
            stateStore.write(session.getId(), new InterviewStateStore.SessionState(
                    InterviewStatus.ASKING.name(), 1, msg.getId()));

            return toVO(session, List.of(toMessageVO(msg)), msg.getId());
        } finally {
            unlockQuietly(lock);
        }
    }

    public MessageVO answer(Long userId, Long sessionId, String answer) {
        RLock lock = lockFor(sessionId);
        try {
            InterviewSession session = requireOwnedSession(userId, sessionId);
            InterviewStateStore.SessionState state = requireAsking(sessionId);

            saveUserMessage(sessionId, "answer", answer);

            List<ChatMessage> messages = decisionMessages(session, buildHistory(sessionId));
            AiDecision d = runDecision(messages, false, null);

            if (d.finished()) {
                InterviewMessage msg = saveAiMessage(sessionId, "feedback", d.text(), null);
                finishSession(session, msg);
                return toMessageVO(msg);
            }

            int newCount = state.questionCount() + 1;
            InterviewMessage msg = saveAiMessage(sessionId, "question", d.text(), null);
            session.setStatus(InterviewStatus.ASKING.name());
            session.setCurrentQuestionId(msg.getId());
            session.setQuestionCount(newCount);
            sessionMapper.updateById(session);
            stateStore.write(sessionId, new InterviewStateStore.SessionState(
                    InterviewStatus.ASKING.name(), newCount, msg.getId()));

            return toMessageVO(msg);
        } finally {
            unlockQuietly(lock);
        }
    }

    public SseEmitter answerStream(Long userId, Long sessionId, String answer) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Thread.ofVirtual().start(() -> {
            RLock lock = null;
            try {
                lock = lockFor(sessionId);
                InterviewSession session = requireOwnedSession(userId, sessionId);
                InterviewStateStore.SessionState state = requireAsking(sessionId);

                saveUserMessage(sessionId, "answer", answer);

                List<ChatMessage> messages = decisionMessages(session, buildHistory(sessionId));
                boolean forceFinish = state.questionCount() >= MAX_QUESTIONS;
                Consumer<String> onToken = token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

                AiDecision d = runDecision(messages, forceFinish, onToken);

                if (d.finished()) {
                    InterviewMessage msg = saveAiMessage(sessionId, "feedback", d.text(), null);
                    finishSession(session, msg);
                    emitter.send(SseEmitter.event().name("done")
                            .data(Map.of("messageId", msg.getId(), "finished", true)));
                } else {
                    int newCount = state.questionCount() + 1;
                    InterviewMessage msg = saveAiMessage(sessionId, "question", d.text(), null);
                    session.setStatus(InterviewStatus.ASKING.name());
                    session.setCurrentQuestionId(msg.getId());
                    session.setQuestionCount(newCount);
                    sessionMapper.updateById(session);
                    stateStore.write(sessionId, new InterviewStateStore.SessionState(
                            InterviewStatus.ASKING.name(), newCount, msg.getId()));
                    emitter.send(SseEmitter.event().name("done")
                            .data(Map.of("messageId", msg.getId(), "finished", false)));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("stream answer failed: {}", e.getMessage());
                emitter.completeWithError(e);
            } finally {
                if (lock != null) {
                    unlockQuietly(lock);
                }
            }
        });
        return emitter;
    }

    public List<InterviewSessionVO> listByUser(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getId));
        return sessions.stream().map(s -> toVO(s, List.of(), null)).toList();
    }

    public InterviewSessionVO detail(Long userId, Long sessionId) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        List<MessageVO> messages = messageMapper.selectList(
                        new LambdaQueryWrapper<InterviewMessage>()
                                .eq(InterviewMessage::getSessionId, sessionId)
                                .orderByAsc(InterviewMessage::getId))
                .stream().map(this::toMessageVO).toList();
        return toVO(session, messages, session.getCurrentQuestionId());
    }

    public InterviewResultVO result(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        InterviewResult row = resultMapper.selectOne(
                new LambdaQueryWrapper<InterviewResult>()
                        .eq(InterviewResult::getSessionId, sessionId));
        if (row == null) {
            return null;
        }
        try {
            Map<String, InterviewResultVO.DimensionVO> dims = new LinkedHashMap<>();
            JsonNode root = objectMapper.readTree(row.getDimensions());
            root.fields().forEachRemaining(e -> dims.put(e.getKey(), e.getValue().isNumber()
                    ? new InterviewResultVO.DimensionVO(e.getValue().asInt(), "")
                    : objectMapper.convertValue(e.getValue(), InterviewResultVO.DimensionVO.class)));
            List<String> weak = new ArrayList<>();
            objectMapper.readTree(row.getWeakPoints()).forEach(n -> weak.add(n.asText()));
            List<String> suggestions = new ArrayList<>();
            objectMapper.readTree(row.getSuggestions()).forEach(n -> suggestions.add(n.asText()));
            return new InterviewResultVO(sessionId, dims, row.getTotalScore(), weak, suggestions, row.getCreatedAt());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "评分结果解析失败");
        }
    }

    private List<ChatMessage> decisionMessages(InterviewSession session, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(session.getTopic(), session.getLevel())));
        messages.addAll(history);
        return messages;
    }

    private ChatRequest decisionRequest(List<ChatMessage> messages) {
        ChatRequest.Builder builder = ChatRequest.builder(aiProperties.activeProvider().getChatModel())
                .messages(messages)
                .temperature(0.7);
        if (aiProperties.activeProvider().isToolsEnabled()) {
            builder.tools(interviewTools());
        }
        return builder.build();
    }

    /**
     * 统一决策入口：始终走流式（避免部分供应商非流式+工具调用异常缓慢）。
     * finishMode=true 时不带工具，直接让模型输出总结。
     */
    private AiDecision runDecision(List<ChatMessage> messages, boolean finishMode, Consumer<String> onToken) {
        StringBuilder acc = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        Consumer<String> sink = token -> {
            acc.append(token);
            if (onToken != null) {
                onToken.accept(token);
            }
        };

        ChatRequest request;
        if (finishMode) {
            List<ChatMessage> msgs = new ArrayList<>(messages);
            msgs.add(ChatMessage.user("本场面试到此结束，请为候选人写一段 150 字以内的整体表现总结，直接输出总结文本。"));
            request = ChatRequest.builder(aiProperties.activeProvider().getChatModel())
                    .messages(msgs)
                    .temperature(0.5)
                    .build();
        } else {
            request = decisionRequest(messages);
        }

        chatClient.chatStreamTools(request, sink, calls -> {
            if (calls != null && !calls.isEmpty()) {
                toolCalls.addAll(calls);
            }
        });

        if (finishMode) {
            String summary = acc.toString().trim();
            if (summary.isBlank()) {
                throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 未返回总结");
            }
            return new AiDecision(true, summary);
        }
        return applyDecision(new ChatResponse(acc.toString().trim(), toolCalls, "tool_calls"));
    }

    private AiDecision applyDecision(ChatResponse resp) {
        if (resp.hasToolCalls()) {
            ToolCall tc = resp.toolCalls().get(0);
            try {
                JsonNode args = objectMapper.readTree(tc.arguments());
                if ("finish_interview".equals(tc.name())) {
                    return new AiDecision(true, args.path("summary").asText("面试结束"));
                }
                if ("ask_question".equals(tc.name())) {
                    String q = args.path("question").asText(null);
                    if (q != null && !q.isBlank()) {
                        return new AiDecision(false, q.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("parse tool args failed: {}", e.getMessage());
            }
        }
        String content = resp.content();
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 未返回有效问题");
        }
        return new AiDecision(false, content.trim());
    }

    private List<ChatTool> interviewTools() {
        ObjectNode askParams = objectMapper.createObjectNode();
        askParams.put("type", "object");
        askParams.putObject("properties").putObject("question")
                .put("type", "string").put("description", "下一道面试问题");
        askParams.putArray("required").add("question");

        ObjectNode finishParams = objectMapper.createObjectNode();
        finishParams.put("type", "object");
        finishParams.putObject("properties").putObject("summary")
                .put("type", "string").put("description", "对候选人整体表现的简短总结");
        finishParams.putArray("required").add("summary");

        return List.of(
                new ChatTool("ask_question", "继续面试，提出下一道面试问题", askParams),
                new ChatTool("finish_interview", "结束面试并给出整体表现的简短总结", finishParams));
    }

    private void finishSession(InterviewSession session, InterviewMessage msg) {
        session.setStatus(InterviewStatus.FINISHED.name());
        session.setCurrentQuestionId(msg.getId());
        session.setFinishedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        stateStore.write(session.getId(), new InterviewStateStore.SessionState(
                InterviewStatus.FINISHED.name(), session.getQuestionCount(), msg.getId()));
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.SCORING_EXCHANGE, RabbitConfig.SCORING_ROUTING_KEY,
                    new InterviewScoringService.ScoringMessage(session.getId()));
            log.info("已投递评分任务 session={}", session.getId());
        } catch (Exception e) {
            log.error("评分任务投递失败 session={}", session.getId(), e);
        }
    }

    private RLock lockFor(Long sessionId) {
        RLock lock = redissonClient.getLock("interview:lock:" + sessionId);
        try {
            // 不指定 leaseTime：Redisson 看门狗自动续期，防止长 AI 调用期间锁过期
            if (!lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BizException(ResultCode.INTERVIEW_STATE_ERROR, "有并发操作进行中，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.INTERVIEW_STATE_ERROR, "获取会话锁被中断");
        }
        return lock;
    }

    private void unlockQuietly(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private InterviewStateStore.SessionState requireAsking(Long sessionId) {
        InterviewStateStore.SessionState state = stateStore.read(sessionId);
        if (state == null) {
            InterviewSession s = sessionMapper.selectById(sessionId);
            if (s == null) {
                throw new BizException(ResultCode.INTERVIEW_NOT_FOUND);
            }
            state = new InterviewStateStore.SessionState(
                    s.getStatus(), s.getQuestionCount(), s.getCurrentQuestionId());
            stateStore.write(sessionId, state);
        }
        if (InterviewStatus.FINISHED.name().equals(state.status())) {
            throw new BizException(ResultCode.INTERVIEW_FINISHED);
        }
        if (!InterviewStatus.ASKING.name().equals(state.status())
                && !InterviewStatus.START.name().equals(state.status())) {
            throw new BizException(ResultCode.INTERVIEW_STATE_ERROR, "当前状态不允许提交答案");
        }
        return state;
    }

    private String buildSystemPrompt(String topic, String level) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getTopic, topic));
        List<String> names = points.stream().map(KnowledgePoint::getName).toList();
        if (aiProperties.activeProvider().isToolsEnabled()) {
            return """
                    你是一名资深 %s 技术面试官，面试级别：%s。
                    本主题覆盖的知识点包括：%s。
                    通过连续提问考察候选人的知识深度，本场面试最多提 %d 个问题。你必须调用工具来推进面试：
                    - 需要继续考察时，调用 ask_question，把下一道问题填入 question 参数；
                    - 当考察已充分或候选人回答明显不合格/已足够时，调用 finish_interview 结束面试，summary 参数给出对候选人整体表现的简短总结。
                    问题要具体、有深度，直击原理与实现细节，不要停留在概念背诵层面；根据候选人上一轮的回答自然地追问、挖坑或换角度。
                    """.formatted(topic, level, String.join("、", names), MAX_QUESTIONS);
        }
        return """
                你是一名资深 %s 技术面试官，面试级别：%s。
                本主题覆盖的知识点包括：%s。
                通过连续提问考察候选人的知识深度，本场面试最多提 %d 个问题。
                每次只输出一道面试问题，问题要具体、有深度，直击原理与实现细节，不要停留在概念背诵层面；
                根据候选人上一轮的回答自然地追问、挖坑或换角度。只输出问题文本本身，不要任何解释、编号、前后缀。
                """.formatted(topic, level, String.join("、", names), MAX_QUESTIONS);
    }

    private boolean topicExists(String topic) {
        return knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getTopic, topic)) > 0;
    }

    private InterviewSession requireOwnedSession(Long userId, Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.INTERVIEW_NOT_FOUND);
        }
        return session;
    }

    private List<ChatMessage> buildHistory(Long sessionId) {
        List<InterviewMessage> rows = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));
        return rows.stream()
                .map(m -> new ChatMessage("AI".equals(m.getRole()) ? "assistant" : "user", m.getContent()))
                .toList();
    }

    private InterviewMessage saveAiMessage(Long sessionId, String kind, String content, Long kpId) {        InterviewMessage msg = new InterviewMessage();
        msg.setSessionId(sessionId);
        msg.setRole("AI");
        msg.setKind(kind);
        msg.setContent(content);
        msg.setKnowledgePointId(kpId);
        messageMapper.insert(msg);
        return msg;
    }

    private InterviewMessage saveUserMessage(Long sessionId, String kind, String content) {
        InterviewMessage msg = new InterviewMessage();
        msg.setSessionId(sessionId);
        msg.setRole("USER");
        msg.setKind(kind);
        msg.setContent(content);
        messageMapper.insert(msg);
        return msg;
    }

    private InterviewSessionVO toVO(InterviewSession s, List<MessageVO> messages, Long currentQuestionId) {
        return new InterviewSessionVO(
                s.getId(), s.getTopic(), s.getLevel(), s.getStatus(), s.getQuestionCount(),
                s.getTotalScore(), s.getStartedAt(), s.getFinishedAt(),
                messages.stream()
                        .filter(m -> m.getId().equals(currentQuestionId))
                        .findFirst()
                        .orElse(null),
                messages);
    }

    private MessageVO toMessageVO(InterviewMessage m) {
        return new MessageVO(
                m.getId(), m.getRole(), m.getKind(), m.getContent(),
                m.getKnowledgePointId(), m.getCreatedAt());
    }
}