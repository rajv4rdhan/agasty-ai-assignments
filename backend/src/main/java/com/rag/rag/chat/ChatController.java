package com.rag.rag.chat;

import com.rag.rag.conversation.Conversation;
import com.rag.rag.conversation.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatService chatService;
    private final StreamingChatService streamingChatService;
    private final EnhancedChatService enhancedChatService;
    
    public ChatController(ChatService chatService, StreamingChatService streamingChatService, 
                         EnhancedChatService enhancedChatService) {
        this.chatService = chatService;
        this.streamingChatService = streamingChatService;
        this.enhancedChatService = enhancedChatService;
    }
    
    @PostMapping
    public ResponseEntity<ChatService.ChatResponse> chat(@RequestBody ChatService.ChatRequest request) {
        ChatService.ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/enhanced")
    public ResponseEntity<ChatService.ChatResponse> enhancedChat(@RequestBody ChatService.ChatRequest request) {
        ChatService.ChatResponse response = enhancedChatService.chat(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatService.ChatRequest request) {
        return streamingChatService.streamChat(request);
    }
    
    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> listConversations() {
        List<Conversation> conversations = chatService.listConversations();
        return ResponseEntity.ok(conversations);
    }
    
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<Message>> getConversationHistory(@PathVariable Long id) {
        List<Message> messages = chatService.getConversationHistory(id);
        return ResponseEntity.ok(messages);
    }
}
