package com.aiview.interview.service;

import com.aiview.agent.ai.ChatClient;
import com.aiview.agent.ai.ChatMessage;
import com.aiview.agent.ai.ChatRequest;
import com.aiview.agent.ai.ChatResponse;
import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.aiview.config.AiProperties;
import com.aiview.interview.dto.CreateInterviewRequest;
import com.aiview.interview.dto.InterviewSessionVO;
import com.aiview.interview.dto.MessageVO;
import com.aiview.interview.dto.TopicVO;
import com.aiview.interview.entity.InterviewMessage;
import com.aiview.interview.entity.InterviewSession;
import com.aiview.interview.entity.KnowledgePoint;
import com.aiview.interview.mapper.InterviewMessageMapper;
import com.aiview.interview.mapper.InterviewSessionMapper;
import com.aiview.interview.mapper.KnowledgePointMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ChatClient chatClient;
    private final AiProperties aiProperties;

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

    @Transactional
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

        String question = askQuestion(session, List.of());
        InterviewMessage msg = saveAiMessage(session.getId(), "question", question, null);

        session.setStatus(InterviewStatus.ASKING.name());
        session.setCurrentQuestionId(msg.getId());
        session.setQuestionCount(1);
        sessionMapper.updateById(session);

        return toVO(session, List.of(toMessageVO(msg)), msg.getId());
    }

    @Transactional
    public MessageVO answer(Long userId, Long sessionId, String answer) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        assertAsking(session);

        saveUserMessage(sessionId, "answer", answer);

        List<ChatMessage> history = buildHistory(sessionId);
        String next = askQuestion(session, history);

        InterviewMessage msg = saveAiMessage(sessionId, "question", next, null);
        session.setStatus(InterviewStatus.ASKING.name());
        session.setCurrentQuestionId(msg.getId());
        session.setQuestionCount(session.getQuestionCount() + 1);
        sessionMapper.updateById(session);

        return toMessageVO(msg);
    }

    public SseEmitter answerStream(Long userId, Long sessionId, String answer) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Thread.ofVirtual().start(() -> {
            try {
                InterviewSession session = requireOwnedSession(userId, sessionId);
                assertAsking(session);

                saveUserMessage(sessionId, "answer", answer);

                List<ChatMessage> history = buildHistory(sessionId);
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(ChatMessage.system(buildSystemPrompt(session.getTopic(), session.getLevel())));
                messages.addAll(history);

                String model = aiProperties.activeProvider().getChatModel();
                StringBuilder acc = new StringBuilder();
                Consumer<String> onToken = token -> {
                    acc.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

                chatClient.chatStream(ChatRequest.builder(model).messages(messages).temperature(0.7).build(), onToken);

                String content = acc.toString().trim();
                if (content.isEmpty()) {
                    throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 未返回有效问题");
                }

                InterviewMessage msg = saveAiMessage(sessionId, "question", content, null);
                session.setStatus(InterviewStatus.ASKING.name());
                session.setCurrentQuestionId(msg.getId());
                session.setQuestionCount(session.getQuestionCount() + 1);
                sessionMapper.updateById(session);

                emitter.send(SseEmitter.event().name("done").data(Map.of("messageId", msg.getId())));
                emitter.complete();
            } catch (Exception e) {
                log.error("stream answer failed: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void assertAsking(InterviewSession session) {
        if (InterviewStatus.FINISHED.name().equals(session.getStatus())) {
            throw new BizException(ResultCode.INTERVIEW_FINISHED);
        }
        if (!InterviewStatus.ASKING.name().equals(session.getStatus())
                && !InterviewStatus.START.name().equals(session.getStatus())) {
            throw new BizException(ResultCode.INTERVIEW_STATE_ERROR, "当前状态不允许提交答案");
        }
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

    private String askQuestion(InterviewSession session, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(session.getTopic(), session.getLevel())));
        messages.addAll(history);

        String model = aiProperties.activeProvider().getChatModel();
        ChatResponse resp = chatClient.chat(ChatRequest.builder(model)
                .messages(messages)
                .temperature(0.7)
                .build());
        String content = resp.content();
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.AI_SERVICE_ERROR, "AI 未返回有效问题");
        }
        return content.trim();
    }

    private String buildSystemPrompt(String topic, String level) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getTopic, topic));
        List<String> names = points.stream().map(KnowledgePoint::getName).toList();
        return """
                你是一名资深 %s 技术面试官，面试级别：%s。
                本主题覆盖的知识点包括：%s。
                你需要通过连续提问考察候选人的知识深度，每次只输出一个面试问题。
                要求：
                1. 问题要具体、有深度，直击原理与实现细节，不要停留在概念背诵层面；
                2. 根据候选人上一轮的答案，自然地追问、挖坑或换角度；
                3. 只输出问题文本本身，不要任何解释、编号、前后缀。
                """.formatted(topic, level, String.join("、", names));
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

    private InterviewMessage saveAiMessage(Long sessionId, String kind, String content, Long kpId) {
        InterviewMessage msg = new InterviewMessage();
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
                        .findFirst().orElse(null),
                messages);
    }

    private MessageVO toMessageVO(InterviewMessage m) {
        return new MessageVO(
                m.getId(), m.getRole(), m.getKind(), m.getContent(),
                m.getKnowledgePointId(), m.getCreatedAt());
    }
}