package com.rag.rag.retrieval;

import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.document.ChunkRepository;
import com.rag.rag.document.EmbeddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {
    
    @Mock
    private ChunkRepository chunkRepository;
    
    @Mock
    private EmbeddingService embeddingService;
    
    private RetrievalService retrievalService;
    
    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService(chunkRepository, embeddingService, 5, 0.7);
        TenantContext.setTenantId("test-tenant");
    }
    
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
    
    @Test
    void testRetrieve_WithoutCategory() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        
        Object[] row = new Object[]{
            1L, "test-tenant", 1L, 0, "test content", 1, null, Instant.now().toString(), 0.85
        };
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(chunkRepository.findSimilarChunks(anyString(), anyString(), anyDouble(), anyInt()))
            .thenReturn(rows);
        
        List<RetrievalService.RetrievedChunk> results = retrievalService.retrieve("test query", null);
        
        assertEquals(1, results.size());
        assertEquals("test content", results.get(0).content());
        assertEquals(0.85, results.get(0).similarity());
        verify(chunkRepository, times(1)).findSimilarChunks(eq("test-tenant"), anyString(), eq(0.7), eq(5));
    }
    
    @Test
    void testRetrieve_WithCategory() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingService.embed(anyString())).thenReturn(embedding);
        when(chunkRepository.findSimilarChunksByCategory(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
            .thenReturn(List.of());
        
        List<RetrievalService.RetrievedChunk> results = retrievalService.retrieve("test query", "math");
        
        assertEquals(0, results.size());
        verify(chunkRepository, times(1)).findSimilarChunksByCategory(
            eq("test-tenant"), eq("math"), anyString(), eq(0.7), eq(5)
        );
    }
}
