package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class KnowledgeMapVO {

    private List<NodeVO> nodes;
    private List<LinkVO> links;

    @Getter
    @AllArgsConstructor
    public static class NodeVO {
        private String id;
        private String name;
        private String category;
        private int mastery;
        private int difficulty;
    }

    @Getter
    @AllArgsConstructor
    public static class LinkVO {
        private String source;
        private String target;
    }
}