package com.aiview.interview.service;

import com.aiview.interview.dto.DashboardVO;
import com.aiview.interview.entity.InterviewResult;
import com.aiview.interview.entity.InterviewSession;
import com.aiview.interview.mapper.InterviewResultMapper;
import com.aiview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户数据分析：跨面试聚合四维平均分、成绩趋势与薄弱点，结果缓存于 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InterviewResultMapper resultMapper;
    private final InterviewSessionMapper sessionMapper;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_MINUTES = 10;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private static final Map<String, String> DIMENSION_NAMES = Map.of(
            "knowledge", "知识掌握",
            "expression", "表达清晰",
            "source", "实践来源",
            "analysis", "问题分析"
    );

    public DashboardVO build(Long userId) {
        String key = "dashboard:user:" + userId;
        RBucket<String> bucket = redissonClient.getBucket(key);
        String cached = bucket.get();
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardVO.class);
            } catch (Exception e) {
                log.warn("parse dashboard cache failed, userId={}: {}", userId, e.getMessage());
            }
        }

        DashboardVO vo = compute(userId);
        try {
            bucket.set(objectMapper.writeValueAsString(vo), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("write dashboard cache failed, userId={}: {}", userId, e.getMessage());
        }
        return vo;
    }

    private DashboardVO compute(Long userId) {
        List<InterviewResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<InterviewResult>().eq(InterviewResult::getUserId, userId));
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, "FINISHED")
                        .isNotNull(InterviewSession::getTotalScore)
                        .orderByAsc(InterviewSession::getFinishedAt));

        Map<String, int[]> dimSums = new LinkedHashMap<>();
        dimSums.put("knowledge", new int[2]);
        dimSums.put("expression", new int[2]);
        dimSums.put("source", new int[2]);
        dimSums.put("analysis", new int[2]);

        for (InterviewResult r : results) {
            try {
                JsonNode dims = objectMapper.readTree(r.getDimensions());
                dims.fields().forEachRemaining(e -> {
                    int[] acc = dimSums.get(e.getKey());
                    if (acc == null) {
                        return;
                    }
                    int score = e.getValue().isNumber() ? e.getValue().asInt()
                            : e.getValue().path("score").asInt(0);
                    acc[0] += score;
                    acc[1]++;
                });
            } catch (Exception ex) {
                log.warn("parse result dimensions failed, resultId={}: {}", r.getId(), ex.getMessage());
            }
        }

        List<DashboardVO.RadarDimVO> dimensions = new ArrayList<>();
        List<String> weakPoints = new ArrayList<>();
        int worst = Integer.MAX_VALUE;
        String worstName = "";
        for (Map.Entry<String, int[]> e : dimSums.entrySet()) {
            int avg = e.getValue()[1] == 0 ? 0 : Math.round((float) e.getValue()[0] / e.getValue()[1]);
            dimensions.add(new DashboardVO.RadarDimVO(DIMENSION_NAMES.getOrDefault(e.getKey(), e.getKey()), avg));
            if (avg < worst) {
                worst = avg;
                worstName = DIMENSION_NAMES.getOrDefault(e.getKey(), e.getKey());
            }
        }
        if (worstName.isEmpty()) {
            worstName = "暂无数据";
        }
        weakPoints.add("最薄弱维度：" + worstName + "（平均 " + worst + "/10 分）");

        int totalScoreSum = sessions.stream().mapToInt(InterviewSession::getTotalScore).sum();
        int avgScore = sessions.isEmpty() ? 0 : Math.round((float) totalScoreSum / sessions.size());
        int maxScore = sessions.stream().mapToInt(InterviewSession::getTotalScore).max().orElse(0);
        int totalQuestions = sessions.stream().mapToInt(s -> s.getQuestionCount() == null ? 0 : s.getQuestionCount()).sum();

        List<DashboardVO.TrendPointVO> trend = new ArrayList<>();
        for (InterviewSession s : sessions) {
            LocalDateTime at = s.getFinishedAt();
            trend.add(new DashboardVO.TrendPointVO(
                    at == null ? "" : at.format(DATE_FMT),
                    s.getTotalScore()));
        }

        List<String> suggestions = new ArrayList<>();
        for (InterviewResult r : results) {
            if (r.getSuggestions() == null) {
                continue;
            }
            try {
                JsonNode arr = objectMapper.readTree(r.getSuggestions());
                arr.forEach(n -> {
                    String s = n.asText();
                    if (suggestions.size() < 5 && !suggestions.contains(s)) {
                        suggestions.add(s);
                    }
                });
            } catch (Exception ex) {
                log.warn("parse suggestions failed, resultId={}: {}", r.getId(), ex.getMessage());
            }
        }

        DashboardVO.StatsVO stats = new DashboardVO.StatsVO(sessions.size(), avgScore, maxScore, totalQuestions);
        return new DashboardVO(stats, dimensions, trend, weakPoints, suggestions);
    }
}