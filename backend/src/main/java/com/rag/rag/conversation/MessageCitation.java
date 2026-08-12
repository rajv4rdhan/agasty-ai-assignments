package com.rag.rag.conversation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_citations")
public class MessageCitation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    
    @Column(name = "document_title", nullable = false, length = 500)
    private String documentTitle;
    
    @Column(name = "page_number")
    private Integer pageNumber;
    
    @Column(name = "similarity_score", nullable = false)
    private Double similarityScore;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    
    public MessageCitation() {}
    
    public MessageCitation(Long messageId, Long documentId, String documentTitle, 
                          Integer pageNumber, Double similarityScore, String snippet) {
        this.messageId = messageId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.similarityScore = similarityScore;
        this.snippet = snippet;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }
    
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
