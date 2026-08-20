package com.aiview.interview.service;

import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 面试会话运行时状态，存于 Redis，作为状态机的单一权威来源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewStateStore {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final long TTL_HOURS = 24;

    public record SessionState(String status, int questionCount, Long currentQuestionId) {
    }

    private String key(Long sessionId) {
        return "interview:session:" + sessionId;
    }

    public SessionState read(Long sessionId) {
        RBucket<String> bucket = redissonClient.getBucket(key(sessionId));
        String json = bucket.get();
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SessionState.class);
        } catch (Exception e) {
            log.warn("parse session state failed, sessionId={}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    public void write(Long sessionId, SessionState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redissonClient.getBucket(key(sessionId)).set(json, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("write session state failed, sessionId={}: {}", sessionId, e.getMessage());
            throw new BizException(ResultCode.INTERNAL_ERROR, "会话状态写入失败");
        }
    }

    public void delete(Long sessionId) {
        redissonClient.getBucket(key(sessionId)).delete();
    }
}