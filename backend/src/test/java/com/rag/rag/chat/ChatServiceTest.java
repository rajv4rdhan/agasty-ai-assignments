package com.rag.rag.chat;

import com.rag.rag.common.exception.ResourceNotFoundException;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.config.ConversationProperties;
import com.rag.rag.conversation.*;
import com.rag.rag.document.Document;
import com.rag.rag.document.DocumentRepository;
import com.rag.rag.retrieval.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    
    @Mock
    private ConversationRepository conversationRepository;
    
    @Mock
    private MessageRepository messageRepository;
    
    @Mock
    private MessageCitationRepository citationRepository;
    
    @Mock
    private DocumentRepository documentRepository;
    
    @Mock
    private RetrievalService retrievalService;
    
    @Mock
    private LlmService llmService;
    
    @Mock
    private ConversationProperties conversationProperties;
    
    @InjectMocks
    private ChatService chatService;
    
    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
    }
    
    @Test
    void chat_shouldRetrieveLastNTurns_whenMaxTurnsIsSet() {
        // Arrange
        Long conversationId = 1L;
        String tenantId = "test-tenant";
        int maxTurns = 2;
        
        Conversation conversation = new Conversation(tenantId, "Test Conversation");
        conversation.setId(conversationId);
        
        // Create 6 messages (3 turns)
        List<Message> allMessages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message userMsg = new Message(conversationId, "user", "Question " + (i + 1));
            userMsg.setId((long) (i * 2 + 1));
            allMessages.add(userMsg);
            
            Message assistantMsg = new Message(conversationId, "assistant", "Answer " + (i + 1));
            assistantMsg.setId((long) (i * 2 + 2));
            allMessages.add(assistantMsg);
        }
        
        when(conversationProperties.getMaxTurns()).thenReturn(maxTurns);
        when(conversationRepository.findByIdAndTenantId(conversationId, tenantId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(allMessages);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(100L);
            }
            return msg;
        });
        
        // Setup retrieval to return chunks
        List<RetrievalService.RetrievedChunk> chunks = List.of(
                new RetrievalService.RetrievedChunk(
                        1L,                     // id
                        "test-tenant",          // tenantId
                        1L,                     // documentId
                        0,                      // chunkIndex
                        "Test content",         // content
                        1,                      // pageNumber
                        null,                   // embedding
                        java.time.Instant.now(), // createdAt
                        0.95                    // similarity
                )
        );
        when(retrievalService.retrieve(anyString(), any())).thenReturn(chunks);
        
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Test Document");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        
        when(llmService.generateAnswer(anyString(), anyString(), anyList()))
                .thenReturn("Generated answer");
        
        ChatService.ChatRequest request = new ChatService.ChatRequest(
                conversationId,
                "New question",
                null
        );
        
        // Act
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("Generated answer", response.answer());
        assertFalse(response.refused());
        
        // Verify that LlmService received only the last 2 turns (4 messages)
        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateAnswer(anyString(), anyString(), historyCaptor.capture());
        
        List<Message> capturedHistory = historyCaptor.getValue();
        assertEquals(4, capturedHistory.size(), "Should include last 2 turns (4 messages)");
        
        // Verify it's the last 2 turns
        assertEquals("Question 2", capturedHistory.get(0).getContent());
        assertEquals("Answer 2", capturedHistory.get(1).getContent());
        assertEquals("Question 3", capturedHistory.get(2).getContent());
        assertEquals("Answer 3", capturedHistory.get(3).getContent());
    }
    
    @Test
    void chat_shouldReturnAllMessages_whenHistoryIsSmallerThanMaxTurns() {
        // Arrange
        Long conversationId = 1L;
        String tenantId = "test-tenant";
        int maxTurns = 5;
        
        Conversation conversation = new Conversation(tenantId, "Test Conversation");
        conversation.setId(conversationId);
        
        // Create only 2 messages (1 turn)
        List<Message> allMessages = new ArrayList<>();
        Message userMsg = new Message(conversationId, "user", "Question 1");
        userMsg.setId(1L);
        allMessages.add(userMsg);
        
        Message assistantMsg = new Message(conversationId, "assistant", "Answer 1");
        assistantMsg.setId(2L);
        allMessages.add(assistantMsg);
        
        when(conversationProperties.getMaxTurns()).thenReturn(maxTurns);
        when(conversationRepository.findByIdAndTenantId(conversationId, tenantId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(allMessages);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(100L);
            }
            return msg;
        });
        
        // Setup retrieval to return chunks
        List<RetrievalService.RetrievedChunk> chunks = List.of(
                new RetrievalService.RetrievedChunk(
                        1L,                     // id
                        "test-tenant",          // tenantId
                        1L,                     // documentId
                        0,                      // chunkIndex
                        "Test content",         // content
                        1,                      // pageNumber
                        null,                   // embedding
                        java.time.Instant.now(), // createdAt
                        0.95                    // similarity
                )
        );
        when(retrievalService.retrieve(anyString(), any())).thenReturn(chunks);
        
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Test Document");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        
        when(llmService.generateAnswer(anyString(), anyString(), anyList()))
                .thenReturn("Generated answer");
        
        ChatService.ChatRequest request = new ChatService.ChatRequest(
                conversationId,
                "New question",
                null
        );
        
        // Act
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Assert
        assertNotNull(response);
        
        // Verify that LlmService received all messages
        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateAnswer(anyString(), anyString(), historyCaptor.capture());
        
        List<Message> capturedHistory = historyCaptor.getValue();
        assertEquals(2, capturedHistory.size(), "Should include all messages when less than maxTurns");
    }
    
    @Test
    void chat_shouldReturnEmptyHistory_whenMaxTurnsIsZero() {
        // Arrange
        Long conversationId = 1L;
        String tenantId = "test-tenant";
        int maxTurns = 0;
        
        Conversation conversation = new Conversation(tenantId, "Test Conversation");
        conversation.setId(conversationId);
        
        when(conversationProperties.getMaxTurns()).thenReturn(maxTurns);
        when(conversationRepository.findByIdAndTenantId(conversationId, tenantId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(100L);
            }
            return msg;
        });
        
        // Setup retrieval to return chunks
        List<RetrievalService.RetrievedChunk> chunks = List.of(
                new RetrievalService.RetrievedChunk(
                        1L,                     // id
                        "test-tenant",          // tenantId
                        1L,                     // documentId
                        0,                      // chunkIndex
                        "Test content",         // content
                        1,                      // pageNumber
                        null,                   // embedding
                        java.time.Instant.now(), // createdAt
                        0.95                    // similarity
                )
        );
        when(retrievalService.retrieve(anyString(), any())).thenReturn(chunks);
        
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Test Document");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        
        when(llmService.generateAnswer(anyString(), anyString(), anyList()))
                .thenReturn("Generated answer");
        
        ChatService.ChatRequest request = new ChatService.ChatRequest(
                conversationId,
                "New question",
                null
        );
        
        // Act
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Assert
        assertNotNull(response);
        
        // Verify that LlmService received empty history
        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateAnswer(anyString(), anyString(), historyCaptor.capture());
        
        List<Message> capturedHistory = historyCaptor.getValue();
        assertEquals(0, capturedHistory.size(), "Should include no history when maxTurns is 0");
    }
    
    @Test
    void chat_shouldReturnEmptyHistory_whenConversationIsNew() {
        // Arrange
        String tenantId = "test-tenant";
        int maxTurns = 10;
        
        Conversation conversation = new Conversation(tenantId, "New Conversation");
        conversation.setId(1L);
        
        when(conversationProperties.getMaxTurns()).thenReturn(maxTurns);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of());
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(100L);
            }
            return msg;
        });
        
        // Setup retrieval to return chunks
        List<RetrievalService.RetrievedChunk> chunks = List.of(
                new RetrievalService.RetrievedChunk(
                        1L,                     // id
                        "test-tenant",          // tenantId
                        1L,                     // documentId
                        0,                      // chunkIndex
                        "Test content",         // content
                        1,                      // pageNumber
                        null,                   // embedding
                        java.time.Instant.now(), // createdAt
                        0.95                    // similarity
                )
        );
        when(retrievalService.retrieve(anyString(), any())).thenReturn(chunks);
        
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Test Document");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        
        when(llmService.generateAnswer(anyString(), anyString(), anyList()))
                .thenReturn("Generated answer");
        
        ChatService.ChatRequest request = new ChatService.ChatRequest(
                null,  // New conversation
                "First question",
                null
        );
        
        // Act
        ChatService.ChatResponse response = chatService.chat(request);
        
        // Assert
        assertNotNull(response);
        
        // Verify that LlmService received empty history for new conversation
        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateAnswer(anyString(), anyString(), historyCaptor.capture());
        
        List<Message> capturedHistory = historyCaptor.getValue();
        assertEquals(0, capturedHistory.size(), "Should include no history for new conversation");
    }
}
