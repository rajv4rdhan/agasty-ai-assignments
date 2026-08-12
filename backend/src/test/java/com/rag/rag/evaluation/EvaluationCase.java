package com.rag.rag.evaluation;

import java.util.List;

/**
 * Represents a single evaluation test case for the RAG system.
 */
public class EvaluationCase {
    
    private String id;
    private String category;
    private String tenantId;
    private String question;
    private String expectedBehavior;
    private String expectedSource;
    private List<String> expectedAnswerKeywords;
    private String description;
    
    // Optional fields for specific test types
    private Long conversationId;
    private String previousQuestion;
    private String categoryFilter;
    private String documentToDelete;
    private String otherTenantId;
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getExpectedBehavior() {
        return expectedBehavior;
    }
    
    public void setExpectedBehavior(String expectedBehavior) {
        this.expectedBehavior = expectedBehavior;
    }
    
    public String getExpectedSource() {
        return expectedSource;
    }
    
    public void setExpectedSource(String expectedSource) {
        this.expectedSource = expectedSource;
    }
    
    public List<String> getExpectedAnswerKeywords() {
        return expectedAnswerKeywords;
    }
    
    public void setExpectedAnswerKeywords(List<String> expectedAnswerKeywords) {
        this.expectedAnswerKeywords = expectedAnswerKeywords;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getPreviousQuestion() {
        return previousQuestion;
    }
    
    public void setPreviousQuestion(String previousQuestion) {
        this.previousQuestion = previousQuestion;
    }
    
    public String getCategoryFilter() {
        return categoryFilter;
    }
    
    public void setCategoryFilter(String categoryFilter) {
        this.categoryFilter = categoryFilter;
    }
    
    public String getDocumentToDelete() {
        return documentToDelete;
    }
    
    public void setDocumentToDelete(String documentToDelete) {
        this.documentToDelete = documentToDelete;
    }
    
    public String getOtherTenantId() {
        return otherTenantId;
    }
    
    public void setOtherTenantId(String otherTenantId) {
        this.otherTenantId = otherTenantId;
    }
}
