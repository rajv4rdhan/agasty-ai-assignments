package com.rag.rag.conversation;

import java.time.Instant;

/**
 * DTO for listing conversations with metadata.
 * Lightweight representation for list operations.
 */
public class ConversationListDTO {
    
    private Long id;
    private String title;
    private int messageCount;
    private Instant lastMessageTimestamp;
    private Instant createdAt;
    
    public ConversationListDTO() {}
    
    public ConversationListDTO(Long id, String title, int messageCount, Instant lastMessageTimestamp, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.messageCount = messageCount;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.createdAt = createdAt;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
    
    public Instant getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }
    
    public void setLastMessageTimestamp(Instant lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
