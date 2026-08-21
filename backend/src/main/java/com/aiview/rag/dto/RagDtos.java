package com.aiview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class RagDtos {

    @Data
    public static class CreateKbRequest {
        private String name;
        private String description = "";
    }

    @Data
    public static class AddContentRequest {
        private String title;
        private String content;
    }

    @Data
    public static class SearchRequest {
        private String query;
        private Integer topK = 3;
    }

    @Data
    @AllArgsConstructor
    public static class KnowledgeBaseVO {
        private Long id;
        private String name;
        private String description;
        private Integer chunkCount;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class ChunkVO {
        private Long id;
        private Long kbId;
        private String title;
        private String content;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class SearchHitVO {
        private Long chunkId;
        private String kbName;
        private String title;
        private String content;
        private double score;
    }
}