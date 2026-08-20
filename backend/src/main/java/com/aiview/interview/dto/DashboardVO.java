package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DashboardVO {

    private StatsVO stats;
    private List<RadarDimVO> dimensions;
    private List<TrendPointVO> trend;
    private List<String> weakPoints;
    private List<String> suggestions;

    @Data
    @AllArgsConstructor
    public static class StatsVO {
        private long totalInterviews;
        private int avgScore;
        private int maxScore;
        private int totalQuestions;
    }

    @Data
    @AllArgsConstructor
    public static class RadarDimVO {
        private String name;
        private int value;
    }

    @Data
    @AllArgsConstructor
    public static class TrendPointVO {
        private String date;
        private int score;
    }
}