package com.rag.rag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import com.rag.rag.chat.GeminiChatModel;
import com.rag.rag.document.GeminiEmbeddingModel;

@Configuration
public class GeminiConfig {

    @Value("${GEMINI_API_KEY}")
    private String apiKey;

    @Value("${GEMINI_MODEL:gemini-1.5-flash}")
    private String chatModel;

    @Value("${GEMINI_EMBEDDING_MODEL:text-embedding-004}")
    private String embeddingModel;

    @Bean
    public RestClient geminiRestClient() {
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Bean
    @Primary
    public ChatModel chatModel(RestClient geminiRestClient) {
        return new GeminiChatModel(geminiRestClient, apiKey, chatModel);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(RestClient geminiRestClient) {
        return new GeminiEmbeddingModel(geminiRestClient, apiKey, embeddingModel);
    }
}
