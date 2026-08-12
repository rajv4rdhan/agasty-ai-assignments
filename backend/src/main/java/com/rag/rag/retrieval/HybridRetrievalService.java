package com.rag.rag.retrieval;

import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.document.ChunkRepository;
import com.rag.rag.document.DocumentChunk;
import com.rag.rag.document.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval service combining vector search and full-text search.
 * 
 * Approach:
 * 1. Vector search (semantic similarity)
 * 2. Full-text search (PostgreSQL to_tsvector/to_tsquery)
 * 3. Rank fusion (Reciprocal Rank Fusion)
 * 
 * Why hybrid?
 * - Vector search: Good for semantic questions ("How much do I have to pay if I submit late?")
 * - Full-text search: Good for exact terms ("Term 2 late fee")
 */
@Service
public class HybridRetrievalService {
    
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final int topK;
    private final double similarityThreshold;
    private final double vectorWeight;
    private final double fullTextWeight;
    
    public HybridRetrievalService(
            ChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            @Value("${rag.retrieval.top-k:5}") int topK,
            @Value("${rag.retrieval.similarity-threshold:0.7}") double similarityThreshold,
            @Value("${rag.retrieval.vector-weight:0.7}") double vectorWeight,
            @Value("${rag.retrieval.fulltext-weight:0.3}") double fullTextWeight) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.vectorWeight = vectorWeight;
        this.fullTextWeight = fullTextWeight;
    }
    
    /**
     * Retrieve chunks using hybrid search (vector + full-text).
     */
    public List<RetrievalService.RetrievedChunk> retrieve(String query, String category) {
        String tenantId = TenantContext.getTenantId();
        
        // 1. Vector search
        List<ScoredChunk> vectorResults = performVectorSearch(query, category, tenantId);
        
        // 2. Full-text search
        List<ScoredChunk> fullTextResults = performFullTextSearch(query, category, tenantId);
        
        // 3. Fuse rankings using Reciprocal Rank Fusion (RRF)
        List<ScoredChunk> fusedResults = fuseRankings(vectorResults, fullTextResults);
        
        // 4. Convert to RetrievedChunk and return top K
        return fusedResults.stream()
                .limit(topK)
                .map(this::toRetrievedChunk)
                .collect(Collectors.toList());
    }
    
    /**
     * Perform vector similarity search.
     */
    private List<ScoredChunk> performVectorSearch(String query, String category, String tenantId) {
        // Generate query embedding
        float[] queryEmbedding = embeddingService.embed(query);
        String embeddingStr = toPostgresVector(queryEmbedding);
        
        // Retrieve similar chunks (get more candidates for fusion)
        int candidateCount = topK * 3;  // Get 3x more candidates
        
        List<Object[]> results;
        if (category != null && !category.isBlank()) {
            results = chunkRepository.findSimilarChunksByCategory(
                tenantId, category, embeddingStr, similarityThreshold, candidateCount
            );
        } else {
            results = chunkRepository.findSimilarChunks(
                tenantId, embeddingStr, similarityThreshold, candidateCount
            );
        }
        
        // Convert to ScoredChunk
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (Object[] row : results) {
            ScoredChunk chunk = new ScoredChunk(
                ((Number) row[0]).longValue(),  // id
                (String) row[1],                // tenant_id
                ((Number) row[2]).longValue(),  // document_id
                ((Number) row[3]).intValue(),   // chunk_index
                (String) row[4],                // content
                row[5] != null ? ((Number) row[5]).intValue() : null,  // page_number
                Instant.parse(row[7].toString()),  // created_at
                ((Number) row[8]).doubleValue()    // similarity score
            );
            scoredChunks.add(chunk);
        }
        
        return scoredChunks;
    }
    
    /**
     * Perform full-text search using PostgreSQL's text search capabilities.
     */
    private List<ScoredChunk> performFullTextSearch(String query, String category, String tenantId) {
        int candidateCount = topK * 3;  // Get 3x more candidates
        
        List<Object[]> results;
        if (category != null && !category.isBlank()) {
            results = chunkRepository.findByFullTextSearchWithCategory(
                tenantId, category, query, candidateCount
            );
        } else {
            results = chunkRepository.findByFullTextSearch(
                tenantId, query, candidateCount
            );
        }
        
        // Convert to ScoredChunk
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (Object[] row : results) {
            ScoredChunk chunk = new ScoredChunk(
                ((Number) row[0]).longValue(),  // id
                (String) row[1],                // tenant_id
                ((Number) row[2]).longValue(),  // document_id
                ((Number) row[3]).intValue(),   // chunk_index
                (String) row[4],                // content
                row[5] != null ? ((Number) row[5]).intValue() : null,  // page_number
                Instant.parse(row[6].toString()),  // created_at
                ((Number) row[7]).doubleValue()    // ts_rank score
            );
            scoredChunks.add(chunk);
        }
        
        return scoredChunks;
    }
    
    /**
     * Fuse rankings using Reciprocal Rank Fusion (RRF).
     * 
     * RRF formula: score(chunk) = Σ 1 / (k + rank(chunk))
     * where k is a constant (typically 60)
     */
    private List<ScoredChunk> fuseRankings(List<ScoredChunk> vectorResults, List<ScoredChunk> fullTextResults) {
        Map<Long, Double> fusedScores = new HashMap<>();
        Map<Long, ScoredChunk> chunkMap = new HashMap<>();
        
        final int k = 60;  // RRF constant
        
        // Add vector search rankings
        for (int i = 0; i < vectorResults.size(); i++) {
            ScoredChunk chunk = vectorResults.get(i);
            double rrfScore = vectorWeight / (k + i + 1);
            fusedScores.merge(chunk.id, rrfScore, Double::sum);
            chunkMap.putIfAbsent(chunk.id, chunk);
        }
        
        // Add full-text search rankings
        for (int i = 0; i < fullTextResults.size(); i++) {
            ScoredChunk chunk = fullTextResults.get(i);
            double rrfScore = fullTextWeight / (k + i + 1);
            fusedScores.merge(chunk.id, rrfScore, Double::sum);
            chunkMap.putIfAbsent(chunk.id, chunk);
        }
        
        // Sort by fused score
        return fusedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(entry -> {
                    ScoredChunk chunk = chunkMap.get(entry.getKey());
                    return new ScoredChunk(
                        chunk.id, chunk.tenantId, chunk.documentId, chunk.chunkIndex,
                        chunk.content, chunk.pageNumber, chunk.createdAt, entry.getValue()
                    );
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Convert ScoredChunk to RetrievedChunk format.
     */
    private RetrievalService.RetrievedChunk toRetrievedChunk(ScoredChunk scored) {
        return new RetrievalService.RetrievedChunk(
            scored.id,
            scored.tenantId,
            scored.documentId,
            scored.chunkIndex,
            scored.content,
            scored.pageNumber,
            null,  // embedding not needed
            scored.createdAt,
            scored.score
        );
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
    
    /**
     * Internal class for storing chunks with scores during ranking fusion.
     */
    private static class ScoredChunk {
        final Long id;
        final String tenantId;
        final Long documentId;
        final Integer chunkIndex;
        final String content;
        final Integer pageNumber;
        final Instant createdAt;
        final Double score;
        
        ScoredChunk(Long id, String tenantId, Long documentId, Integer chunkIndex,
                    String content, Integer pageNumber, Instant createdAt, Double score) {
            this.id = id;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.pageNumber = pageNumber;
            this.createdAt = createdAt;
            this.score = score;
        }
    }
}
