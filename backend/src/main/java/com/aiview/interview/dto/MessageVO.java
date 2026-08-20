package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private String role;
    private String kind;
    private String content;
    private Long knowledgePointId;
    private LocalDateTime createdAt;
}