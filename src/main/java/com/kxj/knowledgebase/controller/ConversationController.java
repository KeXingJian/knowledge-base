package com.kxj.knowledgebase.controller;

import com.kxj.knowledgebase.dto.ApiResponse;
import com.kxj.knowledgebase.entity.Conversation;
import com.kxj.knowledgebase.entity.Message;
import com.kxj.knowledgebase.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("[AI: 收到聊天请求，sessionId: {}]", request.getSessionId());

        String answer = conversationService.chat(request.getSessionId(), request.getQuestion());

        return ApiResponse.success(ChatResponse.builder()
                .answer(answer)
                .sessionId(request.getSessionId())
                .build());
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<Conversation> getConversation(@PathVariable String sessionId) {
        log.info("[AI: 获取对话，sessionId: {}]", sessionId);
        Conversation conversation = conversationService.getConversation(sessionId);
        return ApiResponse.success(conversation);
    }

    @GetMapping
    public ApiResponse<List<Conversation>> getAllConversations() {
        log.info("[AI: 获取所有对话列表]");
        List<Conversation> conversations = conversationService.getAllConversations();
        return ApiResponse.success(conversations);
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<Message>> getConversationMessages(@PathVariable String sessionId) {
        log.info("[AI: 获取对话消息，sessionId: {}]", sessionId);
        Conversation conversation = conversationService.getConversation(sessionId);
        if (conversation == null) {
            return ApiResponse.success(List.of());
        }
        List<Message> messages = conversationService.getConversationMessages(conversation.getId());
        return ApiResponse.success(messages);
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<String> deleteConversation(@PathVariable String sessionId) {
        log.info("[AI: 删除对话，sessionId: {}]", sessionId);
        conversationService.deleteConversation(sessionId);
        return ApiResponse.success("删除对话成功");
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
