package com.rag.rag.chat;

import com.rag.rag.common.exception.ResourceNotFoundException;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.config.ConversationProperties;
import com.rag.rag.conversation.*;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.retrieval.RetrievalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentRepository documentRepository;
    private final RetrievalService retrievalService;
    private final LlmService llmService;
    private final ConversationProperties conversationProperties;
    
    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            DocumentRepository documentRepository,
            RetrievalService retrievalService,
            LlmService llmService,
            ConversationProperties conversationProperties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.documentRepository = documentRepository;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.conversationProperties = conversationProperties;
    }
    
    @Transactional
    public ChatResponse chat(ChatRequest request) {
        String tenantId = TenantContext.getTenantId();
        
        // Get or create conversation
        Conversation conversation;
        if (request.conversationId() != null) {
            conversation = conversationRepository.findByIdAndTenantId(request.conversationId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        } else {
            conversation = new Conversation(tenantId, "New Conversation");
            conversation = conversationRepository.save(conversation);
        }
        
        // Retrieve conversation history (last N turns)
        List<Message> conversationHistory = getLastNTurns(conversation.getId(), conversationProperties.getMaxTurns());
        
        // Save user message
        Message userMessage = new Message(conversation.getId(), "user", request.message());
        userMessage = messageRepository.save(userMessage);
        
        // Retrieve relevant chunks
        List<RetrievalService.RetrievedChunk> chunks = retrievalService.retrieve(
            request.message(), 
            request.category()
        );
        
        // Check if we have any relevant context
        if (chunks.isEmpty()) {
            String refusalMessage = "I don't have enough information in the available documents to answer your question. " +
                    "Please try rephrasing your question or upload relevant documents.";
            
            Message assistantMessage = new Message(conversation.getId(), "assistant", refusalMessage);
            assistantMessage = messageRepository.save(assistantMessage);
            
            return new ChatResponse(
                conversation.getId(),
                refusalMessage,
                List.of(),
                true
            );
        }
        
        // Build context and get document info
        Map<Long, Document> documentCache = new HashMap<>();
        StringBuilder contextBuilder = new StringBuilder();
        
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalService.RetrievedChunk chunk = chunks.get(i);
            
            // Get document info
            Document doc = documentCache.computeIfAbsent(chunk.documentId(), 
                id -> documentRepository.findById(id).orElse(null));
            
            if (doc != null) {
                contextBuilder.append(String.format(
                    "[Document %d: %s%s]\n%s\n\n",
                    i + 1,
                    doc.getTitle(),
                    chunk.pageNumber() != null ? ", Page " + chunk.pageNumber() : "",
                    chunk.content()
                ));
            }
        }
        
        // Generate answer using LLM with conversation history
        String answer = llmService.generateAnswer(request.message(), contextBuilder.toString(), conversationHistory);
        
        // Save assistant message
        Message assistantMessage = new Message(conversation.getId(), "assistant", answer);
        assistantMessage = messageRepository.save(assistantMessage);
        
        // Save citations
        List<CitationResponse> citations = saveCitations(assistantMessage.getId(), chunks, documentCache);
        
        return new ChatResponse(
            conversation.getId(),
            answer,
            citations,
            false
        );
    }
    
    /**
     * Retrieves the last N turns from conversation history.
     * A turn consists of one user message and one assistant response pair.
     * 
     * @param conversationId the conversation ID
     * @param maxTurns maximum number of turns to retrieve
     * @return list of messages representing the last N turns
     */
    private List<Message> getLastNTurns(Long conversationId, int maxTurns) {
        if (maxTurns <= 0) {
            return List.of();
        }
        
        // Retrieve all messages for the conversation in chronological order
        List<Message> allMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        
        // Calculate the maximum number of messages to include (maxTurns * 2 for user + assistant pairs)
        int maxMessages = maxTurns * 2;
        
        // If we have fewer messages than the limit, return all
        if (allMessages.size() <= maxMessages) {
            return allMessages;
        }
        
        // Otherwise, return the last N turns (last maxMessages messages)
        return allMessages.subList(allMessages.size() - maxMessages, allMessages.size());
    }
    
    private List<CitationResponse> saveCitations(
            Long messageId, 
            List<RetrievalService.RetrievedChunk> chunks,
            Map<Long, Document> documentCache) {
        
        List<CitationResponse> citations = new ArrayList<>();
        
        for (RetrievalService.RetrievedChunk chunk : chunks) {
            Document doc = documentCache.get(chunk.documentId());
            if (doc == null) continue;
            
            String snippet = chunk.content().length() > 200 
                ? chunk.content().substring(0, 200) + "..."
                : chunk.content();
            
            MessageCitation citation = new MessageCitation(
                messageId,
                doc.getId(),
                doc.getTitle(),
                chunk.pageNumber(),
                chunk.similarity(),
                snippet
            );
            citation = citationRepository.save(citation);
            
            citations.add(new CitationResponse(
                doc.getTitle(),
                chunk.pageNumber(),
                chunk.similarity(),
                snippet
            ));
        }
        
        return citations;
    }
    
    public List<Conversation> listConversations() {
        String tenantId = TenantContext.getTenantId();
        return conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }
    
    public List<Message> getConversationHistory(Long conversationId) {
        String tenantId = TenantContext.getTenantId();
        conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
    
    public record ChatRequest(
            Long conversationId,
            String message,
            String category
    ) {}
    
    public record ChatResponse(
            Long conversationId,
            String answer,
            List<CitationResponse> citations,
            boolean refused
    ) {}
    
    public record CitationResponse(
            String documentTitle,
            Integer page,
            Double similarity,
            String snippet
    ) {}
}
