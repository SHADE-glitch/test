package com.aiview.agent.ai;

import java.util.List;
import java.util.function.Consumer;

public interface ChatClient {

    ChatResponse chat(ChatRequest request);

    default List<ChatMessage> withConversation(List<ChatMessage> history) {
        return history;
    }

    default void chatStream(ChatRequest request, Consumer<String> onToken) {
        ChatResponse resp = chat(request);
        if (resp.content() != null && !resp.content().isBlank()) {
            onToken.accept(resp.content());
        }
    }
}