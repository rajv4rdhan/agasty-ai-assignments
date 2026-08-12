package com.rag.rag.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {
    
    private final int chunkSize;
    private final int chunkOverlap;
    
    public ChunkingService(
            @Value("${rag.chunk-size:500}") int chunkSize,
            @Value("${rag.chunk-overlap:50}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }
    
    public List<TextChunk> chunkText(String text, Integer pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.isBlank()) {
            return chunks;
        }
        
        // Limit text size to prevent OOM (e.g., max 10MB of text per page)
        final int MAX_TEXT_LENGTH = 10_000_000; // 10 million characters
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
            System.err.println("Warning: Text truncated to " + MAX_TEXT_LENGTH + " characters to prevent OOM");
        }
        
        int start = 0;
        int index = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // Try to find a sentence boundary near the end
            if (end < text.length()) {
                int sentenceEnd = findSentenceEnd(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }
            
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                chunks.add(new TextChunk(index++, chunkText, pageNumber));
            }
            
            // Move start position with overlap
            start = end - chunkOverlap;
            if (start >= text.length() || start <= 0) break;
        }
        
        return chunks;
    }
    
    private int findSentenceEnd(String text, int start, int position) {
        // Look for sentence endings within a reasonable window
        int windowStart = Math.max(start, position - 50);
        int windowEnd = Math.min(text.length(), position + 50);
        
        // Ensure we don't create an invalid substring
        if (windowStart >= windowEnd || windowStart >= text.length()) {
            return position;
        }
        
        String window = text.substring(windowStart, windowEnd);
        
        // Find the last sentence ending punctuation
        int lastDot = window.lastIndexOf('.');
        int lastQuestion = window.lastIndexOf('?');
        int lastExclamation = window.lastIndexOf('!');
        
        int lastPunctuation = Math.max(lastDot, Math.max(lastQuestion, lastExclamation));
        
        if (lastPunctuation > 0) {
            return windowStart + lastPunctuation + 1;
        }
        
        return position;
    }
    
    public record TextChunk(int index, String text, Integer pageNumber) {}
}
