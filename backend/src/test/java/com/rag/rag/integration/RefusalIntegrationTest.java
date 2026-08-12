package com.rag.rag.integration;

import com.rag.rag.AbstractIntegrationTest;
import com.rag.rag.chat.ChatService;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.document.ChunkRepository;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentChunk;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.document.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the refusal mechanism.
 * When the retrieval service cannot find relevant chunks above the similarity threshold,
 * the system should refuse to answer rather than hallucinate.
 */
class RefusalIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private ChatService chatService;
    
    @Test
    void shouldRefuseWhenNoRelevantDocuments() {
        String tenantId = "test-tenant";
        TenantContext.setTenantId(tenantId);
        
        // Create document with very specific content
        Document document = new Document(
            tenantId,
            "School Schedule",
            "schedule.txt",
            "txt",
            1000L,
            "schedule-hash",
            "academics"
        );
        document.setStatus(DocumentStatus.READY);
        document = documentRepository.save(document);
        
        // Create chunk with specific content about school schedules
        float[] embedding = createDomainEmbedding(768, "schedule");
        DocumentChunk chunk = new DocumentChunk(
            tenantId,
            document.getId(),
            0,
            "School starts at 8:00 AM and ends at 3:00 PM. Lunch is from 12:00 PM to 12:30 PM.",
            null,
            embedding
        );
        chunkRepository.save(chunk);
        
        // Ask a completely unrelated question (about sports, not schedule)
        ChatService.ChatRequest request = new ChatService.ChatRequest(
            null,
            "What are the ingredients for making chocolate chip cookies?",
            null
        );
        
        // Execute chat
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Verify refusal
        assertThat(response.refused()).isTrue();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).containsIgnoringCase("don't have enough information");
        
        TenantContext.clear();
    }
    
    @Test
    void shouldAnswerWhenRelevantDocumentsExist() {
        String tenantId = "test-tenant";
        TenantContext.setTenantId(tenantId);
        
        // Create document with specific content
        Document document = new Document(
            tenantId,
            "School Policies",
            "policies.txt",
            "txt",
            1000L,
            "policies-hash",
            "administration"
        );
        document.setStatus(DocumentStatus.READY);
        document = documentRepository.save(document);
        
        // Create chunk with high similarity to query
        float[] embedding = createDomainEmbedding(768, "attendance");
        DocumentChunk chunk = new DocumentChunk(
            tenantId,
            document.getId(),
            0,
            "Attendance policy: Students must maintain 95% attendance. More than 5 absences require a parent meeting.",
            null,
            embedding
        );
        chunkRepository.save(chunk);
        
        // Ask a related question
        ChatService.ChatRequest request = new ChatService.ChatRequest(
            null,
            "What is the attendance policy?",
            null
        );
        
        // Execute chat
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Verify NOT refused (should have found relevant content)
        assertThat(response.refused()).isFalse();
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.answer()).isNotEmpty();
        
        TenantContext.clear();
    }
    
    /**
     * Creates a test embedding vector with domain-specific variations.
     * This creates embeddings that are more likely to match semantically similar queries.
     */
    private float[] createDomainEmbedding(int dimension, String domain) {
        float[] embedding = new float[dimension];
        int seed = domain.hashCode();
        
        for (int i = 0; i < dimension; i++) {
            // Create pseudo-random values based on domain and index
            double value = Math.sin((seed + i) * 0.1);
            embedding[i] = (float) (value * 0.5 + 0.5);
        }
        
        // Normalize the embedding
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        
        for (int i = 0; i < dimension; i++) {
            embedding[i] /= norm;
        }
        
        return embedding;
    }
}
