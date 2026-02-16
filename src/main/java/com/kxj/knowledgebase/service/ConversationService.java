package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.config.ConversationProperties;
import com.kxj.knowledgebase.constants.CacheConstants;
import com.kxj.knowledgebase.entity.Conversation;
import com.kxj.knowledgebase.entity.Message;
import com.kxj.knowledgebase.repository.ConversationRepository;
import com.kxj.knowledgebase.repository.MessageRepository;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.rag.ChatMessage;
import com.kxj.knowledgebase.service.rag.RAGService;
import com.kxj.knowledgebase.service.retriever.HybridRetriever;
import com.kxj.knowledgebase.service.retriever.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final HybridRetriever hybridRetriever;
    private final RAGService ragService;
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ConversationProperties conversationProperties;

    public Conversation createConversation(String sessionId) {
        log.info("[AI: 创建新对话，sessionId: {}]", sessionId);

        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .title("新对话")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .messageCount(0)
                .messages(new ArrayList<>())
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("[AI: 对话创建成功，conversationId: {}]", conversation.getId());

        return conversation;
    }

    public Conversation getConversation(String sessionId) {
        log.info("[AI: 获取对话，sessionId: {}]", sessionId);
        return conversationRepository.findBySessionId(sessionId).orElse(null);
    }

    public List<Conversation> getAllConversations() {
        log.info("[AI: 获取所有对话列表]");
        return conversationRepository.findAllByOrderByCreateTimeDesc();
    }

    @Transactional
    public void deleteConversation(String sessionId) {
        log.info("[AI: 删除对话，sessionId: {}]", sessionId);
        conversationRepository.deleteBySessionId(sessionId);
        redisTemplate.delete(CacheConstants.CONVERSATION_CACHE_PREFIX + sessionId);
    }

    @Transactional
    public Message addMessage(Long conversationId, String role, String content, String context, String retrievedChunks) {
        log.info("[AI: 添加消息，conversationId: {}, role: {}]", conversationId, role);

        Message message = Message.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .context(context)
                .retrievedChunks(retrievedChunks)
                .createTime(LocalDateTime.now())
                .build();

        message = messageRepository.save(message);

        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setUpdateTime(LocalDateTime.now());
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversationRepository.save(conversation);
        });

        log.info("[AI: 消息添加成功，messageId: {}]", message.getId());
        return message;
    }

    public List<Message> getConversationMessages(Long conversationId) {
        log.info("[AI: 获取对话消息，conversationId: {}]", conversationId);
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId);
    }

    @Transactional
    public String chat(String sessionId, String question) {
        log.info("[AI: 收到对话请求，sessionId: {}, question: {}]", sessionId, question);

        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseGet(() -> createConversation(sessionId));

        List<Message> messages = getConversationMessages(conversation.getId());

        addMessage(conversation.getId(), "user", question, null, null);

        List<ChatMessage> history = messages.stream()
                .filter(m -> messages.indexOf(m) >= Math.max(0, messages.size() - conversationProperties.getMaxHistoryMessages()))
                .map(m -> ChatMessage.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .build())
                .collect(Collectors.toList());

        float[] queryEmbedding = embeddingService.embed(question);

        List<SearchResult> searchResults = hybridRetriever.retrieve(question, queryEmbedding, 3);

        if (searchResults.isEmpty()) {
            log.warn("[AI: 未找到相关文档片段]");
            String answer = "抱歉，我在知识库中没有找到与您问题相关的信息。";
            addMessage(conversation.getId(), "assistant", answer, null, null);
            return answer;
        }

        String context = searchResults.stream()
                .map(result -> result.getChunk().getContent())
                .collect(Collectors.joining("\n\n"));

        String retrievedChunksJson = searchResults.stream()
                .map(result -> String.format("{\"id\":%d,\"content\":\"%s\",\"score\":%.2f,\"source\":\"%s\"}",
                        result.getChunk().getId(),
                        result.getChunk().getContent().replace("\"", "\\\"").replace("\n", "\\n"),
                        result.getScore(),
                        result.getSource()))
                .collect(Collectors.joining(","));

        log.info("[AI: 找到 {} 个相关片段，开始生成回答]", searchResults.size());

        String answer = ragService.answerWithContext(question, context, history);

        addMessage(conversation.getId(), "assistant", answer, context, retrievedChunksJson);

        if (conversation.getMessageCount() == 2) {
            String title = generateTitle(question);
            conversation.setTitle(title);
            conversationRepository.save(conversation);
            log.info("[AI: 对话标题已更新: {}]", title);
        }

        String cacheKey = CacheConstants.CONVERSATION_CACHE_PREFIX + sessionId;
        redisTemplate.opsForValue().set(cacheKey, conversation.getId().toString(), 
                CacheConstants.CONVERSATION_CACHE_TTL, TimeUnit.SECONDS);

        log.info("[AI: 对话完成]");
        return answer;
    }

    private String generateTitle(String question) {
        String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
        return title.replace("\n", " ").trim();
    }
}
