package com.rag.rag.chat;

import com.rag.rag.common.exception.ResourceNotFoundException;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.config.ConversationProperties;
import com.rag.rag.conversation.*;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.retrieval.HybridRetrievalService;
import com.rag.rag.retrieval.QueryRewritingService;
import com.rag.rag.retrieval.RerankingService;
import com.rag.rag.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced chat service with advanced retrieval features:
 * - Hybrid search (vector + full-text)
 * - Query rewriting for follow-up questions
 * - Reranking for better relevance
 */
@Service
public class EnhancedChatService {
    
    private static final Logger log = LoggerFactory.getLogger(EnhancedChatService.class);
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentRepository documentRepository;
    private final HybridRetrievalService hybridRetrievalService;
    private final QueryRewritingService queryRewritingService;
    private final RerankingService rerankingService;
    private final LlmService llmService;
    private final ConversationProperties conversationProperties;
    
    private final boolean useHybridSearch;
    private final boolean useQueryRewriting;
    private final boolean useReranking;
    private final int rerankCandidates;
    private final int finalTopK;
    
    public EnhancedChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            DocumentRepository documentRepository,
            HybridRetrievalService hybridRetrievalService,
            QueryRewritingService queryRewritingService,
            RerankingService rerankingService,
            LlmService llmService,
            ConversationProperties conversationProperties,
            @Value("${rag.retrieval.use-hybrid:true}") boolean useHybridSearch,
            @Value("${rag.retrieval.use-query-rewriting:true}") boolean useQueryRewriting,
            @Value("${rag.retrieval.use-reranking:true}") boolean useReranking,
            @Value("${rag.retrieval.rerank-candidates:15}") int rerankCandidates,
            @Value("${rag.retrieval.top-k:5}") int finalTopK) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.documentRepository = documentRepository;
        this.hybridRetrievalService = hybridRetrievalService;
        this.queryRewritingService = queryRewritingService;
        this.rerankingService = rerankingService;
        this.llmService = llmService;
        this.conversationProperties = conversationProperties;
        this.useHybridSearch = useHybridSearch;
        this.useQueryRewriting = useQueryRewriting;
        this.useReranking = useReranking;
        this.rerankCandidates = rerankCandidates;
        this.finalTopK = finalTopK;
    }
    
    @Transactional
    public ChatService.ChatResponse chat(ChatService.ChatRequest request) {
        String tenantId = TenantContext.getTenantId();
        long startTime = System.currentTimeMillis();
        
        // Get or create conversation
        Conversation conversation;
        if (request.conversationId() != null) {
            conversation = conversationRepository.findByIdAndTenantId(request.conversationId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        } else {
            conversation = new Conversation(tenantId, "New Conversation");
            conversation = conversationRepository.save(conversation);
        }
        
        // 1. Query rewriting (for follow-up questions)
        String originalQuery = request.message();
        String rewrittenQuery = originalQuery;
        
        if (useQueryRewriting && request.conversationId() != null) {
            rewrittenQuery = queryRewritingService.rewriteQuery(originalQuery, request.conversationId());
            if (!rewrittenQuery.equals(originalQuery)) {
                log.info("Query rewritten from '{}' to '{}'", originalQuery, rewrittenQuery);
            }
        }
        
        // Save user message
        Message userMessage = new Message(conversation.getId(), "user", originalQuery);
        userMessage = messageRepository.save(userMessage);
        
        // 2. Retrieve conversation history (last N turns)
        List<Message> conversationHistory = getLastNTurns(conversation.getId(), conversationProperties.getMaxTurns());
        
        // 3. Hybrid retrieval (vector + full-text)
        List<RetrievalService.RetrievedChunk> chunks;
        
        if (useHybridSearch) {
            chunks = hybridRetrievalService.retrieve(rewrittenQuery, request.category());
            log.info("Hybrid search retrieved {} chunks", chunks.size());
        } else {
            // Fallback to standard vector search would go here
            chunks = hybridRetrievalService.retrieve(rewrittenQuery, request.category());
        }
        
        // 4. Reranking (if enabled and we have candidates)
        if (useReranking && chunks.size() > finalTopK) {
            log.info("Reranking {} chunks to top {}", chunks.size(), finalTopK);
            chunks = rerankingService.rerank(rewrittenQuery, chunks, finalTopK);
        }
        
        long retrievalTime = System.currentTimeMillis() - startTime;
        log.info("Retrieval completed in {}ms", retrievalTime);
        
        // Check if we have any relevant context
        if (chunks.isEmpty()) {
            String refusalMessage = "I don't have enough information in the available documents to answer your question. " +
                    "Please try rephrasing your question or upload relevant documents.";
            
            Message assistantMessage = new Message(conversation.getId(), "assistant", refusalMessage);
            assistantMessage = messageRepository.save(assistantMessage);
            
            return new ChatService.ChatResponse(
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
        long llmStartTime = System.currentTimeMillis();
        String answer = llmService.generateAnswer(rewrittenQuery, contextBuilder.toString(), conversationHistory);
        long llmTime = System.currentTimeMillis() - llmStartTime;
        log.info("LLM generation completed in {}ms", llmTime);
        
        // Save assistant message
        Message assistantMessage = new Message(conversation.getId(), "assistant", answer);
        assistantMessage = messageRepository.save(assistantMessage);
        
        // Save citations
        List<ChatService.CitationResponse> citations = saveCitations(assistantMessage.getId(), chunks, documentCache);
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Total request completed in {}ms (retrieval: {}ms, llm: {}ms)", 
            totalTime, retrievalTime, llmTime);
        
        return new ChatService.ChatResponse(
            conversation.getId(),
            answer,
            citations,
            false
        );
    }
    
    /**
     * Retrieves the last N turns from conversation history.
     */
    private List<Message> getLastNTurns(Long conversationId, int maxTurns) {
        if (maxTurns <= 0) {
            return List.of();
        }
        
        List<Message> allMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        
        int maxMessages = maxTurns * 2;
        
        if (allMessages.size() <= maxMessages) {
            return allMessages;
        }
        
        return allMessages.subList(allMessages.size() - maxMessages, allMessages.size());
    }
    
    private List<ChatService.CitationResponse> saveCitations(
            Long messageId, 
            List<RetrievalService.RetrievedChunk> chunks,
            Map<Long, Document> documentCache) {
        
        List<ChatService.CitationResponse> citations = new ArrayList<>();
        
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
            
            citations.add(new ChatService.CitationResponse(
                doc.getTitle(),
                chunk.pageNumber(),
                chunk.similarity(),
                snippet
            ));
        }
        
        return citations;
    }
}
