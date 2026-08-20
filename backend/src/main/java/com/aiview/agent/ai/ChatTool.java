package com.aiview.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record ChatTool(String name, String description, JsonNode parameters) {
}