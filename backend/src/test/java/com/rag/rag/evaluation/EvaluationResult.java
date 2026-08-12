package com.rag.rag.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Results from evaluating a single test case.
 */
public class EvaluationResult {
    
    private String caseId;
    private String category;
    private boolean passed;
    private List<String> failures;
    
    // Detailed metrics
    private boolean retrievalSuccess;
    private boolean refusalCorrectness;
    private boolean citationCorrectness;
    private boolean tenantIsolation;
    private boolean answerQuality;
    
    // Additional context
    private String actualAnswer;
    private List<String> actualSources;
    private boolean actualRefused;
    
    public EvaluationResult(String caseId, String category) {
        this.caseId = caseId;
        this.category = category;
        this.failures = new ArrayList<>();
        this.actualSources = new ArrayList<>();
    }
    
    public void addFailure(String failure) {
        this.failures.add(failure);
        this.passed = false;
    }
    
    public void markPassed() {
        if (failures.isEmpty()) {
            this.passed = true;
        }
    }
    
    // Getters and setters
    public String getCaseId() {
        return caseId;
    }
    
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public List<String> getFailures() {
        return failures;
    }
    
    public void setFailures(List<String> failures) {
        this.failures = failures;
    }
    
    public boolean isRetrievalSuccess() {
        return retrievalSuccess;
    }
    
    public void setRetrievalSuccess(boolean retrievalSuccess) {
        this.retrievalSuccess = retrievalSuccess;
    }
    
    public boolean isRefusalCorrectness() {
        return refusalCorrectness;
    }
    
    public void setRefusalCorrectness(boolean refusalCorrectness) {
        this.refusalCorrectness = refusalCorrectness;
    }
    
    public boolean isCitationCorrectness() {
        return citationCorrectness;
    }
    
    public void setCitationCorrectness(boolean citationCorrectness) {
        this.citationCorrectness = citationCorrectness;
    }
    
    public boolean isTenantIsolation() {
        return tenantIsolation;
    }
    
    public void setTenantIsolation(boolean tenantIsolation) {
        this.tenantIsolation = tenantIsolation;
    }
    
    public boolean isAnswerQuality() {
        return answerQuality;
    }
    
    public void setAnswerQuality(boolean answerQuality) {
        this.answerQuality = answerQuality;
    }
    
    public String getActualAnswer() {
        return actualAnswer;
    }
    
    public void setActualAnswer(String actualAnswer) {
        this.actualAnswer = actualAnswer;
    }
    
    public List<String> getActualSources() {
        return actualSources;
    }
    
    public void setActualSources(List<String> actualSources) {
        this.actualSources = actualSources;
    }
    
    public boolean isActualRefused() {
        return actualRefused;
    }
    
    public void setActualRefused(boolean actualRefused) {
        this.actualRefused = actualRefused;
    }
}
