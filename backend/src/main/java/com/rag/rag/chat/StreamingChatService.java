package com.rag.rag.chat;

import com.rag.rag.common.exception.ResourceNotFoundException;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.conversation.*;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.retrieval.RetrievalService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;

@Service
public class StreamingChatService {
    
    private static final String SYSTEM_PROMPT = """
        You are a helpful assistant for a school document system. 
        Your role is to answer questions based ONLY on the provided context from school documents.
        
        Rules:
        1. Only use information from the provided context to answer questions
        2. If the context doesn't contain enough information to answer, say so clearly
        3. Cite the document title and page number when referencing information
        4. Be concise and accurate
        5. Do not make up information or use knowledge outside the provided context
        """;
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentRepository documentRepository;
    private final RetrievalService retrievalService;
    private final StreamingChatModel streamingChatModel;
    
    public StreamingChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            DocumentRepository documentRepository,
            RetrievalService retrievalService,
            StreamingChatModel streamingChatModel) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.documentRepository = documentRepository;
        this.retrievalService = retrievalService;
        this.streamingChatModel = streamingChatModel;
    }
    
    public SseEmitter streamChat(ChatService.ChatRequest request) {
        SseEmitter emitter = new SseEmitter(30000L); // 30 second timeout
        String tenantId = TenantContext.getTenantId();
        
        // Handle disconnection
        emitter.onCompletion(() -> {
            // Cleanup if needed
        });
        
        emitter.onTimeout(() -> {
            emitter.complete();
        });
        
        emitter.onError((ex) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(new ErrorEvent(ex.getMessage())));
            } catch (IOException e) {
                // Client disconnected
            }
            emitter.complete();
        });
        
        // Process asynchronously
        processStreamingRequest(emitter, request, tenantId);
        
        return emitter;
    }
    
    private void processStreamingRequest(SseEmitter emitter, ChatService.ChatRequest request, String tenantId) {
        try {
            // Get or create conversation
            Conversation conversation;
            if (request.conversationId() != null) {
                conversation = conversationRepository.findByIdAndTenantId(request.conversationId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            } else {
                conversation = new Conversation(tenantId, "New Conversation");
                conversation = conversationRepository.save(conversation);
            }
            
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
                
                emitter.send(SseEmitter.event()
                        .name("content")
                        .data(new ContentEvent(refusalMessage)));
                
                Message assistantMessage = new Message(conversation.getId(), "assistant", refusalMessage);
                messageRepository.save(assistantMessage);
                
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(new DoneEvent(conversation.getId(), true)));
                
                emitter.complete();
                return;
            }
            
            // Build context and get document info
            Map<Long, Document> documentCache = new HashMap<>();
            StringBuilder contextBuilder = new StringBuilder();
            List<CitationDTO> citationDTOs = new ArrayList<>();
            
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
                    
                    String snippet = chunk.content().length() > 200 
                        ? chunk.content().substring(0, 200) + "..."
                        : chunk.content();
                    
                    citationDTOs.add(new CitationDTO(
                        doc.getTitle(),
                        chunk.pageNumber(),
                        chunk.similarity(),
                        snippet
                    ));
                }
            }
            
            // Send retrieval event with citations
            emitter.send(SseEmitter.event()
                    .name("retrieval")
                    .data(new RetrievalEvent(citationDTOs)));
            
            // Build prompt
            String userMessageContent = String.format("""
                Context from school documents:
                
                %s
                
                Question: %s
                
                Answer based only on the context above. If you cannot answer from the context, say so.
                """, contextBuilder.toString(), request.message());
            
            List<org.springframework.ai.chat.messages.Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userMessageContent)
            );
            
            Prompt prompt = new Prompt(messages);
            
            // Stream LLM response
            StringBuilder fullAnswer = new StringBuilder();
            Flux<String> contentFlux = streamingChatModel.stream(prompt)
                    .map(chatResponse -> {
                        if (chatResponse.getResult() != null && 
                            chatResponse.getResult().getOutput() != null) {
                            return chatResponse.getResult().getOutput().getContent();
                        }
                        return "";
                    })
                    .filter(content -> content != null && !content.isEmpty());
            
            Long finalConversationId = conversation.getId();
            contentFlux.subscribe(
                token -> {
                    try {
                        fullAnswer.append(token);
                        emitter.send(SseEmitter.event()
                                .name("content")
                                .data(new ContentEvent(token)));
                    } catch (IOException e) {
                        // Client disconnected
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(new ErrorEvent(error.getMessage())));
                    } catch (IOException e) {
                        // Client disconnected
                    }
                    emitter.complete();
                },
                () -> {
                    try {
                        // Save assistant message
                        Message assistantMessage = new Message(finalConversationId, "assistant", fullAnswer.toString());
                        Message saved = messageRepository.save(assistantMessage);
                        
                        // Save citations
                        for (int i = 0; i < chunks.size(); i++) {
                            RetrievalService.RetrievedChunk chunk = chunks.get(i);
                            Document doc = documentCache.get(chunk.documentId());
                            if (doc == null) continue;
                            
                            String snippet = chunk.content().length() > 200 
                                ? chunk.content().substring(0, 200) + "..."
                                : chunk.content();
                            
                            MessageCitation citation = new MessageCitation(
                                saved.getId(),
                                doc.getId(),
                                doc.getTitle(),
                                chunk.pageNumber(),
                                chunk.similarity(),
                                snippet
                            );
                            citationRepository.save(citation);
                        }
                        
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(new DoneEvent(finalConversationId, false)));
                        emitter.complete();
                    } catch (IOException e) {
                        // Client disconnected
                        emitter.complete();
                    }
                }
            );
            
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(new ErrorEvent(e.getMessage())));
            } catch (IOException ex) {
                // Client disconnected
            }
            emitter.complete();
        }
    }
    
    // Event DTOs
    public record RetrievalEvent(List<CitationDTO> citations) {}
    
    public record CitationDTO(
            String documentTitle,
            Integer page,
            Double similarity,
            String snippet
    ) {}
    
    public record ContentEvent(String token) {}
    
    public record DoneEvent(Long conversationId, boolean refused) {}
    
    public record ErrorEvent(String errorMessage) {}
}
