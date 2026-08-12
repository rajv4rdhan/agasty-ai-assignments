package com.rag.rag.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeminiEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiEmbeddingModel(RestClient restClient, String apiKey, String model) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        
        for (String text : request.getInstructions()) {
            float[] embedding = embed(text);
            embeddings.add(new Embedding(embedding, 0));
        }
        
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getContent());
    }

    public float[] embed(String text) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", text);
        content.put("parts", List.of(part));
        requestBody.put("content", content);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/models/{model}:embedContent?key={apiKey}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractEmbedding(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Gemini Embedding API: " + e.getMessage(), e);
        }
    }

    private float[] extractEmbedding(Map<String, Object> response) {
        try {
            Map<String, Object> embedding = (Map<String, Object>) response.get("embedding");
            List<Double> values = (List<Double>) embedding.get("values");
            
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract embedding from response", e);
        }
    }
}
