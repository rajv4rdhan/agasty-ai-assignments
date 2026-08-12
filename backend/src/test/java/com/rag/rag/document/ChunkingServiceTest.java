package com.rag.rag.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {
    
    private final ChunkingService chunkingService = new ChunkingService(100, 20);
    
    @Test
    void testChunkText_WithSmallText() {
        String text = "This is a small text.";
        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText(text, 1);
        
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text());
        assertEquals(1, chunks.get(0).pageNumber());
    }
    
    @Test
    void testChunkText_WithLargeText() {
        String text = "a".repeat(250);
        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText(text, null);
        
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.get(0).text().length() <= 100);
    }
    
    @Test
    void testChunkText_WithEmptyText() {
        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText("", null);
        assertTrue(chunks.isEmpty());
    }
    
    @Test
    void testChunkText_WithNullText() {
        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText(null, null);
        assertTrue(chunks.isEmpty());
    }
}
