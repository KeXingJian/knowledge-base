package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.entity.Conversation;
import com.kxj.knowledgebase.entity.Message;
import com.kxj.knowledgebase.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("[AI: 收到聊天请求，sessionId: {}]", request.getSessionId());

        String answer = conversationService.chat(request.getSessionId(), request.getQuestion());

        return ResponseEntity.ok(ChatResponse.builder()
                .answer(answer)
                .sessionId(request.getSessionId())
                .build());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Conversation> getConversation(@PathVariable String sessionId) {
        log.info("[AI: 获取对话，sessionId: {}]", sessionId);
        Conversation conversation = conversationService.getConversation(sessionId);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping
    public ResponseEntity<List<Conversation>> getAllConversations() {
        log.info("[AI: 获取所有对话列表]");
        List<Conversation> conversations = conversationService.getAllConversations();
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<Message>> getConversationMessages(@PathVariable String sessionId) {
        log.info("[AI: 获取对话消息，sessionId: {}]", sessionId);
        Conversation conversation = conversationService.getConversation(sessionId);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }
        List<Message> messages = conversationService.getConversationMessages(conversation.getId());
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String sessionId) {
        log.info("[AI: 删除对话，sessionId: {}]", sessionId);
        conversationService.deleteConversation(sessionId);
        return ResponseEntity.noContent().build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatRequest {
        private String sessionId;
        private String question;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatResponse {
        private String answer;
        private String sessionId;
    }
}
