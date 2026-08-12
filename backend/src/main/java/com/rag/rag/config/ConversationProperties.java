package com.rag.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.conversation")
public class ConversationProperties {
    
    /**
     * Maximum number of conversation turns to include in context.
     * A turn consists of one user message and one assistant response.
     */
    private int maxTurns = 10;
    
    /**
     * Maximum number of tokens allowed in conversation history context.
     * If conversation history exceeds this limit, it will be truncated.
     */
    private int maxTokens = 4000;
    
    public int getMaxTurns() {
        return maxTurns;
    }
    
    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
