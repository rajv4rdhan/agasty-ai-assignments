package com.rag.rag.integration;

import com.rag.rag.AbstractIntegrationTest;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.conversation.*;
import com.rag.rag.document.*;
import com.rag.rag.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying cascading document deletion.
 * Ensures that when a document is deleted, all associated chunks and citations
 * are also deleted, and the document no longer appears in retrieval results.
 */
class DocumentDeletionIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ChunkRepository chunkRepository;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private MessageCitationRepository citationRepository;
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private RetrievalService retrievalService;
    
    @Test
    void shouldCascadeDeleteDocumentAndExcludeFromRetrieval() {
        String tenantId = "test-tenant";
        TenantContext.setTenantId(tenantId);
        
        // Create document
        Document document = new Document(
            tenantId,
            "Test Document",
            "test.txt",
            "txt",
            1000L,
            "test-hash",
            "test-category"
        );
        document.setStatus(DocumentStatus.READY);
        document = documentRepository.save(document);
        
        // Create chunk with embedding
        float[] embedding = createTestEmbedding(768);
        DocumentChunk chunk = new DocumentChunk(
            tenantId,
            document.getId(),
            0,
            "Test content for retrieval",
            null,
            embedding
        );
        chunk = chunkRepository.save(chunk);
        
        // Create conversation and message with citation
        Conversation conversation = new Conversation(tenantId, "Test Conversation");
        conversation = conversationRepository.save(conversation);
        
        Message message = new Message(conversation.getId(), "assistant", "Test response");
        message = messageRepository.save(message);
        
        MessageCitation citation = new MessageCitation(
            message.getId(),
            document.getId(),
            document.getTitle(),
            null,
            0.95,
            "Test snippet"
        );
        citation = citationRepository.save(citation);
        
        Long documentId = document.getId();
        Long chunkId = chunk.getId();
        Long citationId = citation.getId();
        
        // Verify document exists and is retrievable
        List<RetrievalService.RetrievedChunk> beforeDeletion = retrievalService.retrieve("test query", null);
        assertThat(beforeDeletion).isNotEmpty();
        assertThat(beforeDeletion).anyMatch(c -> c.documentId().equals(documentId));
        
        // Verify citation exists
        assertThat(citationRepository.findById(citationId)).isPresent();
        
        // Delete document
        documentService.deleteDocument(documentId, tenantId);
        
        // Verify document is deleted
        assertThat(documentRepository.findById(documentId)).isEmpty();
        
        // Verify chunk is deleted
        assertThat(chunkRepository.findById(chunkId)).isEmpty();
        
        // Verify citation is deleted
        assertThat(citationRepository.findById(citationId)).isEmpty();
        
        // Verify retrieval no longer returns chunks from deleted document
        List<RetrievalService.RetrievedChunk> afterDeletion = retrievalService.retrieve("test query", null);
        assertThat(afterDeletion).noneMatch(c -> c.documentId().equals(documentId));
        
        TenantContext.clear();
    }
    
    /**
     * Creates a test embedding vector of dimension 768.
     * Values are set to create a valid embedding for similarity search.
     */
    private float[] createTestEmbedding(int dimension) {
        float[] embedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            embedding[i] = (float) Math.sin(i * 0.1) * 0.5f + 0.5f;
        }
        return embedding;
    }
}
