package com.rag.rag.integration;

import com.rag.rag.AbstractIntegrationTest;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentChunk;
import com.rag.rag.document.ChunkRepository;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.document.DocumentStatus;
import com.rag.rag.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying tenant data isolation.
 * Ensures that documents and chunks for one tenant are never visible to another tenant.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private RetrievalService retrievalService;
    
    @Test
    void shouldIsolateTenantData() {
        // Setup: Create documents and chunks for two different tenants
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        
        // Create document for tenant A
        Document docA = new Document(
            tenantA,
            "Tenant A Document",
            "doc-a.txt",
            "txt",
            1000L,
            "hash-a",
            "category-a"
        );
        docA.setStatus(DocumentStatus.READY);
        docA = documentRepository.save(docA);
        
        // Create chunk for tenant A with embedding
        float[] embeddingA = createTestEmbedding(768, 0.5f);
        DocumentChunk chunkA = new DocumentChunk(
            tenantA,
            docA.getId(),
            0,
            "Content for tenant A",
            null,
            embeddingA
        );
        chunkRepository.save(chunkA);
        
        // Create document for tenant B
        Document docB = new Document(
            tenantB,
            "Tenant B Document",
            "doc-b.txt",
            "txt",
            1000L,
            "hash-b",
            "category-b"
        );
        docB.setStatus(DocumentStatus.READY);
        docB = documentRepository.save(docB);
        
        // Create chunk for tenant B with similar embedding
        float[] embeddingB = createTestEmbedding(768, 0.5f);
        DocumentChunk chunkB = new DocumentChunk(
            tenantB,
            docB.getId(),
            0,
            "Content for tenant B",
            null,
            embeddingB
        );
        chunkRepository.save(chunkB);
        
        // Test: Retrieve as tenant A
        TenantContext.setTenantId(tenantA);
        List<RetrievalService.RetrievedChunk> resultsA = retrievalService.retrieve("test query", null);
        
        // Verify: Only tenant A's chunks are returned
        assertThat(resultsA).isNotEmpty();
        assertThat(resultsA).allMatch(chunk -> chunk.tenantId().equals(tenantA));
        assertThat(resultsA).noneMatch(chunk -> chunk.tenantId().equals(tenantB));
        
        // Test: Retrieve as tenant B
        TenantContext.setTenantId(tenantB);
        List<RetrievalService.RetrievedChunk> resultsB = retrievalService.retrieve("test query", null);
        
        // Verify: Only tenant B's chunks are returned
        assertThat(resultsB).isNotEmpty();
        assertThat(resultsB).allMatch(chunk -> chunk.tenantId().equals(tenantB));
        assertThat(resultsB).noneMatch(chunk -> chunk.tenantId().equals(tenantA));
        
        // Cleanup context
        TenantContext.clear();
    }
    
    /**
     * Creates a test embedding vector of the specified dimension.
     * All values are set to the same constant for simplicity.
     */
    private float[] createTestEmbedding(int dimension, float value) {
        float[] embedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            embedding[i] = value;
        }
        return embedding;
    }
}
