package com.rag.rag.retrieval;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reranking service to improve retrieval quality.
 * 
 * Flow:
 * question → top 10-20 vector chunks → reranker → best 5 → LLM
 * 
 * Approach:
 * Use LLM to score relevance of each chunk to the query on a scale of 1-10.
 * This provides more nuanced relevance scoring than pure vector similarity.
 * 
 * Why rerank?
 * - Vector search can miss semantic nuances
 * - Reranking with LLM provides better relevance assessment
 * - Reduces noise sent to final LLM call
 */
@Service
public class RerankingService {
    
    private static final String RERANKING_PROMPT = """
        You are a relevance scoring assistant. Your task is to score how relevant 
        a text passage is to answering a given question.
        
        Scoring Guidelines:
        10 - Perfectly answers the question, contains exact information needed
        8-9 - Highly relevant, contains most of the needed information
        6-7 - Moderately relevant, contains some useful context
        4-5 - Tangentially relevant, might provide background
        1-3 - Not relevant, different topic or no useful information
        
        Rules:
        1. Be objective and precise in scoring
        2. Consider semantic relevance, not just keyword matching
        3. Higher score if the passage directly answers the question
        4. Lower score if the passage is only vaguely related
        
        Format your response EXACTLY as:
        Score: [number]
        
        Do not include any other text or explanation.
        """;
    
    private final ChatModel chatModel;
    
    public RerankingService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    /**
     * Rerank chunks based on relevance to query.
     * 
     * @param query The user's question
     * @param chunks List of candidate chunks (typically 10-20)
     * @param topK Number of top chunks to return after reranking (typically 5)
     * @return Reranked and filtered list of top K chunks
     */
    public List<RetrievalService.RetrievedChunk> rerank(
            String query, 
            List<RetrievalService.RetrievedChunk> chunks, 
            int topK) {
        
        if (chunks.isEmpty()) {
            return chunks;
        }
        
        // Score each chunk
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        
        for (RetrievalService.RetrievedChunk chunk : chunks) {
            double relevanceScore = scoreRelevance(query, chunk.content());
            scoredChunks.add(new ScoredChunk(chunk, relevanceScore));
        }
        
        // Sort by score descending and return top K
        return scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());
    }
    
    /**
     * Score relevance of a chunk to the query using LLM.
     * Returns a score from 1-10.
     */
    private double scoreRelevance(String query, String chunkContent) {
        try {
            // Truncate chunk if too long (to save tokens)
            String truncatedContent = chunkContent.length() > 500 
                ? chunkContent.substring(0, 500) + "..." 
                : chunkContent;
            
            String userPrompt = String.format("""
                Question: %s
                
                Passage: %s
                
                Score the relevance of this passage to answering the question.
                """, query, truncatedContent);
            
            List<org.springframework.ai.chat.messages.Message> messages = List.of(
                new SystemMessage(RERANKING_PROMPT),
                new UserMessage(userPrompt)
            );
            
            Prompt prompt = new Prompt(messages);
            String response = chatModel.call(prompt).getResult().getOutput().getContent().trim();
            
            // Parse score from response
            return parseScore(response);
            
        } catch (Exception e) {
            // If scoring fails, return neutral score
            return 5.0;
        }
    }
    
    /**
     * Parse relevance score from LLM response.
     * Expected format: "Score: 8"
     */
    private double parseScore(String response) {
        Pattern pattern = Pattern.compile("Score:\\s*(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                // Clamp score between 1 and 10
                return Math.max(1.0, Math.min(10.0, score));
            } catch (NumberFormatException e) {
                return 5.0;
            }
        }
        
        // Try to find any number in the response as fallback
        pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)");
        matcher = pattern.matcher(response);
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                return Math.max(1.0, Math.min(10.0, score));
            } catch (NumberFormatException e) {
                return 5.0;
            }
        }
        
        return 5.0; // Default neutral score
    }
    
    /**
     * Batch reranking for better efficiency.
     * Scores multiple chunks in a single LLM call.
     */
    public List<RetrievalService.RetrievedChunk> rerankBatch(
            String query, 
            List<RetrievalService.RetrievedChunk> chunks, 
            int topK) {
        
        if (chunks.isEmpty()) {
            return chunks;
        }
        
        // For batch reranking, we'd format all chunks in one prompt
        // For simplicity in this implementation, we use individual scoring
        // Production implementation should optimize with batching
        return rerank(query, chunks, topK);
    }
    
    /**
     * Internal class for storing chunks with scores.
     */
    private record ScoredChunk(RetrievalService.RetrievedChunk chunk, double score) {}
}
