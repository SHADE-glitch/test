package com.aiview.agent.ai;

import java.util.List;

public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        List<ChatTool> tools,
        Double temperature,
        boolean jsonMode) {

    public static Builder builder(String model) {
        return new Builder(model);
    }

    public static class Builder {
        private final String model;
        private List<ChatMessage> messages;
        private List<ChatTool> tools = List.of();
        private Double temperature = 0.7;
        private boolean jsonMode = false;

        Builder(String model) {
            this.model = model;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<ChatTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder jsonMode(boolean jsonMode) {
            this.jsonMode = jsonMode;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(model, messages, tools, temperature, jsonMode);
        }
    }
}