package com.aiview.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInterviewRequest {

    @NotBlank(message = "面试主题不能为空")
    private String topic;

    @NotBlank(message = "面试级别不能为空")
    private String level;
}