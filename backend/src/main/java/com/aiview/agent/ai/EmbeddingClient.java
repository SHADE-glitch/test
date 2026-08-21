package com.aiview.agent.ai;

import java.util.List;

public interface EmbeddingClient {

    float[] embed(String text);

    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}