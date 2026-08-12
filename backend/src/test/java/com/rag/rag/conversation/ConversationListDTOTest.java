package com.rag.rag.conversation;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConversationListDTOTest {
    
    @Test
    void testConstructorAndGetters() {
        Long id = 1L;
        String title = "Test Conversation";
        int messageCount = 5;
        Instant lastMessageTimestamp = Instant.now();
        Instant createdAt = Instant.now().minusSeconds(3600);
        
        ConversationListDTO dto = new ConversationListDTO(
            id, title, messageCount, lastMessageTimestamp, createdAt
        );
        
        assertEquals(id, dto.getId());
        assertEquals(title, dto.getTitle());
        assertEquals(messageCount, dto.getMessageCount());
        assertEquals(lastMessageTimestamp, dto.getLastMessageTimestamp());
        assertEquals(createdAt, dto.getCreatedAt());
    }
    
    @Test
    void testSetters() {
        ConversationListDTO dto = new ConversationListDTO();
        
        Long id = 2L;
        String title = "Updated Title";
        int messageCount = 10;
        Instant lastMessageTimestamp = Instant.now();
        Instant createdAt = Instant.now().minusSeconds(7200);
        
        dto.setId(id);
        dto.setTitle(title);
        dto.setMessageCount(messageCount);
        dto.setLastMessageTimestamp(lastMessageTimestamp);
        dto.setCreatedAt(createdAt);
        
        assertEquals(id, dto.getId());
        assertEquals(title, dto.getTitle());
        assertEquals(messageCount, dto.getMessageCount());
        assertEquals(lastMessageTimestamp, dto.getLastMessageTimestamp());
        assertEquals(createdAt, dto.getCreatedAt());
    }
    
    @Test
    void testNoArgsConstructor() {
        ConversationListDTO dto = new ConversationListDTO();
        assertNotNull(dto);
        assertNull(dto.getId());
        assertNull(dto.getTitle());
        assertEquals(0, dto.getMessageCount());
        assertNull(dto.getLastMessageTimestamp());
        assertNull(dto.getCreatedAt());
    }
}
