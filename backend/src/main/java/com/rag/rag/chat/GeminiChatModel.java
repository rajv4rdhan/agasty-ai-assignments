package com.rag.rag.chat;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeminiChatModel implements ChatModel {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiChatModel(RestClient restClient, String apiKey, String model) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        
        // Convert messages to Gemini format
        List<Map<String, Object>> geminiContents = messages.stream()
                .map(msg -> {
                    Map<String, Object> content = new HashMap<>();
                    Map<String, String> part = new HashMap<>();
                    part.put("text", msg.getContent());
                    content.put("parts", List.of(part));
                    content.put("role", msg instanceof UserMessage ? "user" : "model");
                    return content;
                })
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", geminiContents);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String generatedText = extractGeneratedText(response);
            
            AssistantMessage assistantMessage = new AssistantMessage(generatedText);
            Generation generation = new Generation(assistantMessage);
            return new ChatResponse(List.of(generation));
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }

    private String extractGeneratedText(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
