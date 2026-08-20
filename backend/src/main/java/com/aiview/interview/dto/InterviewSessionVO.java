package com.aiview.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class InterviewSessionVO {

    private Long id;
    private String topic;
    private String level;
    private String status;
    private Integer questionCount;
    private Integer totalScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private MessageVO currentQuestion;
    private List<MessageVO> messages;
}