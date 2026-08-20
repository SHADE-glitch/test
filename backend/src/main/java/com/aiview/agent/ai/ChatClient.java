package com.aiview.agent.ai;

import java.util.List;

public interface ChatClient {

    ChatResponse chat(ChatRequest request);

    default List<ChatMessage> withConversation(List<ChatMessage> history) {
        return history;
    }
}