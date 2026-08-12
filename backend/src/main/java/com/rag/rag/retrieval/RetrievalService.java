package com.rag.rag.retrieval;

import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.document.ChunkRepository;
import com.rag.rag.document.DocumentChunk;
import com.rag.rag.document.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class RetrievalService {
    
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final int topK;
    private final double similarityThreshold;
    
    public RetrievalService(
            ChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            @Value("${rag.retrieval.top-k:5}") int topK,
            @Value("${rag.retrieval.similarity-threshold:0.7}") double similarityThreshold) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }
    
    public List<RetrievedChunk> retrieve(String query, String category) {
        String tenantId = TenantContext.getTenantId();
        
        // Generate query embedding
        float[] queryEmbedding = embeddingService.embed(query);
        String embeddingStr = toPostgresVector(queryEmbedding);
        
        // Retrieve similar chunks from database
        List<Object[]> results;
        if (category != null && !category.isBlank()) {
            results = chunkRepository.findSimilarChunksByCategory(
                tenantId, category, embeddingStr, similarityThreshold, topK
            );
        } else {
            results = chunkRepository.findSimilarChunks(
                tenantId, embeddingStr, similarityThreshold, topK
            );
        }
        
        // Convert to RetrievedChunk objects
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (Object[] row : results) {
            RetrievedChunk chunk = new RetrievedChunk(
                ((Number) row[0]).longValue(),  // id
                (String) row[1],                // tenant_id
                ((Number) row[2]).longValue(),  // document_id
                ((Number) row[3]).intValue(),   // chunk_index
                (String) row[4],                // content
                row[5] != null ? ((Number) row[5]).intValue() : null,  // page_number
                null,                           // embedding (not needed)
                Instant.parse(row[7].toString()),  // created_at
                ((Number) row[8]).doubleValue() // similarity
            );
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    private String toPostgresVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    public record RetrievedChunk(
            Long id,
            String tenantId,
            Long documentId,
            Integer chunkIndex,
            String content,
            Integer pageNumber,
            float[] embedding,
            Instant createdAt,
            Double similarity
    ) {}
}
