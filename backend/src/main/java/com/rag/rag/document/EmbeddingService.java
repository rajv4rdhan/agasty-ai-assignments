package com.rag.rag.document;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    private final int batchSize;
    
    public EmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${rag.batch-size:10}") int batchSize) {
        this.embeddingModel = embeddingModel;
        this.batchSize = batchSize;
    }
    
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            EmbeddingRequest request = new EmbeddingRequest(batch, null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            response.getResults().forEach(result -> {
                allEmbeddings.add(result.getOutput());
            });
        }
        
        return allEmbeddings;
    }
    
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().get(0).getOutput();
    }
}
