package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TopicVO {

    private String topic;
    private List<KnowledgePointVO> points;

    @Data
    @AllArgsConstructor
    public static class KnowledgePointVO {
        private Long id;
        private String name;
        private Integer difficulty;
    }
}