package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class InterviewResultVO {

    private Long sessionId;
    private Map<String, DimensionVO> dimensions;
    private Integer totalScore;
    private List<String> weakPoints;
    private List<String> suggestions;
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    public static class DimensionVO {
        private Integer score;
        private String comment;
    }
}