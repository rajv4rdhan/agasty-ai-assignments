package com.rag.rag.retrieval;

import com.rag.rag.conversation.Message;
import com.rag.rag.conversation.MessageRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query rewriting service for handling follow-up questions in conversations.
 * 
 * Purpose:
 * Transform context-dependent follow-up questions into standalone queries.
 * 
 * Example:
 * Previous: "What is the transport fee?"
 * User: "What about class 9?"
 * Rewritten: "What is the transport fee for class 9?"
 * 
 * This improves retrieval quality by making queries self-contained.
 */
@Service
public class QueryRewritingService {
    
    private static final String REWRITING_PROMPT = """
        You are a query rewriting assistant. Your task is to rewrite follow-up questions 
        into standalone queries that can be understood without conversation context.
        
        Rules:
        1. Incorporate necessary context from the conversation history
        2. Make the question self-contained and clear
        3. Preserve the user's intent and any specific details they mentioned
        4. If the question is already standalone, return it unchanged
        5. Do not add information that wasn't in the conversation
        6. Keep the rewritten query concise
        
        Examples:
        
        Conversation:
        User: "What is the transport fee?"
        Assistant: "The transport fee is Rs. 500 per month."
        User: "What about class 9?"
        
        Rewritten: "What is the transport fee for class 9?"
        
        Conversation:
        User: "What are the admission requirements?"
        Assistant: "The admission requirements include..."
        User: "When is the deadline?"
        
        Rewritten: "When is the deadline for admission?"
        
        Conversation:
        User: "What is the late fee policy?"
        Assistant: "The late fee is 10% of the total amount."
        User: "How is it calculated?"
        
        Rewritten: "How is the late fee calculated?"
        """;
    
    private final ChatModel chatModel;
    private final MessageRepository messageRepository;
    
    public QueryRewritingService(ChatModel chatModel, MessageRepository messageRepository) {
        this.chatModel = chatModel;
        this.messageRepository = messageRepository;
    }
    
    /**
     * Rewrite a query based on conversation history.
     * Returns the original query if no conversation context or if already standalone.
     */
    public String rewriteQuery(String query, Long conversationId) {
        // If no conversation context, return original query
        if (conversationId == null) {
            return query;
        }
        
        // Get recent conversation history (last 3 turns = 6 messages)
        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        
        if (history.isEmpty()) {
            return query;
        }
        
        // Get last 3 turns (6 messages max)
        int startIndex = Math.max(0, history.size() - 6);
        List<Message> recentHistory = history.subList(startIndex, history.size());
        
        // Check if query seems like a follow-up (heuristic)
        if (!seemsLikeFollowUp(query)) {
            return query;
        }
        
        // Build conversation context
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Conversation history:\n");
        for (Message msg : recentHistory) {
            contextBuilder.append(String.format("%s: %s\n", 
                msg.getRole().equals("user") ? "User" : "Assistant", 
                msg.getContent()));
        }
        contextBuilder.append(String.format("\nUser: %s\n", query));
        contextBuilder.append("\nRewrite the last user query to be standalone. " +
                              "Return ONLY the rewritten query, nothing else.");
        
        // Call LLM to rewrite
        try {
            List<org.springframework.ai.chat.messages.Message> messages = List.of(
                new SystemMessage(REWRITING_PROMPT),
                new UserMessage(contextBuilder.toString())
            );
            
            Prompt prompt = new Prompt(messages);
            String rewritten = chatModel.call(prompt).getResult().getOutput().getContent().trim();
            
            // Clean up response (remove quotes if LLM added them)
            rewritten = rewritten.replaceAll("^\"|\"$", "");
            
            // If rewriting fails or returns something weird, use original
            if (rewritten.isEmpty() || rewritten.length() > query.length() * 3) {
                return query;
            }
            
            return rewritten;
            
        } catch (Exception e) {
            // If rewriting fails, return original query
            return query;
        }
    }
    
    /**
     * Heuristic to detect if a query seems like a follow-up question.
     * 
     * Indicators:
     * - Short length (< 30 characters)
     * - Starts with pronouns or follow-up words
     * - Contains referential words without context
     */
    private boolean seemsLikeFollowUp(String query) {
        String lowerQuery = query.toLowerCase().trim();
        
        // Check length
        if (lowerQuery.length() < 30) {
            // Check for follow-up patterns
            String[] followUpPatterns = {
                "what about",
                "how about",
                "and for",
                "what if",
                "when is",
                "where is",
                "how much",
                "can i",
                "is it",
                "does it",
                "will it",
                "that one",
                "this one",
                "the same",
                "also"
            };
            
            for (String pattern : followUpPatterns) {
                if (lowerQuery.startsWith(pattern) || lowerQuery.contains(" " + pattern)) {
                    return true;
                }
            }
            
            // Check if starts with pronoun or reference
            String[] referenceStarters = {
                "it", "they", "he", "she", "this", "that", "these", "those"
            };
            
            for (String starter : referenceStarters) {
                if (lowerQuery.startsWith(starter + " ")) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
