package com.rag.rag.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmService {
    
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
    
    private final ChatModel chatModel;
    
    public LlmService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    public String generateAnswer(String question, String context, List<com.rag.rag.conversation.Message> conversationHistory) {
        List<Message> messages = new ArrayList<>();
        
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        
        // Add conversation history
        for (com.rag.rag.conversation.Message historyMessage : conversationHistory) {
            if ("user".equals(historyMessage.getRole())) {
                messages.add(new UserMessage(historyMessage.getContent()));
            } else if ("assistant".equals(historyMessage.getRole())) {
                messages.add(new org.springframework.ai.chat.messages.AssistantMessage(historyMessage.getContent()));
            }
        }
        
        String userMessage = String.format("""
            Context from school documents:
            
            %s
            
            Question: %s
            
            Answer based only on the context above. If you cannot answer from the context, say so.
            """, context, question);
        
        messages.add(new UserMessage(userMessage));
        
        Prompt prompt = new Prompt(messages);
        return chatModel.call(prompt).getResult().getOutput().getContent();
    }
}
