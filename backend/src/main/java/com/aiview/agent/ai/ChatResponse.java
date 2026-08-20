package com.aiview.agent.ai;

import java.util.List;

public record ChatResponse(String content, List<ToolCall> toolCalls, String finishReason) {

    public static ChatResponse text(String content) {
        return new ChatResponse(content, List.of(), "stop");
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}