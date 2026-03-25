package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.config.ConversationProperties;
import com.kxj.knowledgebase.constants.CacheConstants;
import com.kxj.knowledgebase.dto.ChatMessage;
import com.kxj.knowledgebase.dto.SearchResult;
import com.kxj.knowledgebase.entity.Conversation;
import com.kxj.knowledgebase.entity.Message;
import com.kxj.knowledgebase.repository.ConversationRepository;
import com.kxj.knowledgebase.repository.MessageRepository;
import com.kxj.knowledgebase.service.cache.SemanticCacheService;
import com.kxj.knowledgebase.service.embedding.CachedEmbeddingService;
import com.kxj.knowledgebase.service.rag.RAGService;
import com.kxj.knowledgebase.service.retriever.HybridRetriever;
import com.kxj.knowledgebase.service.retriever.ParentAwareHybridRetriever;
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
    private final ParentAwareHybridRetriever parentAwareHybridRetriever;
    private final RAGService ragService;
    private final CachedEmbeddingService cachedEmbeddingService;
    private final SemanticCacheService semanticCacheService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ConversationProperties conversationProperties;

    public Conversation createConversation(String sessionId) {
        log.info("[创建新对话，sessionId: {}]", sessionId);

        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .title("新对话")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .messageCount(0)
                .messages(new ArrayList<>())
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("[对话创建成功，conversationId: {}]", conversation.getId());

        return conversation;
    }

    public Conversation getConversation(String sessionId) {
        log.info("[获取对话，sessionId: {}]", sessionId);
        return conversationRepository.findBySessionId(sessionId).orElse(null);
    }

    public List<Conversation> getAllConversations() {
        log.info("[获取所有对话列表]");
        return conversationRepository.findAllByOrderByCreateTimeDesc();
    }

    @Transactional
    public void deleteConversation(String sessionId) {
        log.info("[删除对话，sessionId: {}]", sessionId);
        conversationRepository.deleteBySessionId(sessionId);
        redisTemplate.delete(CacheConstants.CONVERSATION_CACHE_PREFIX + sessionId);
    }

    @Transactional
    public void addMessage(Long conversationId, String role, String content, String context, String retrievedChunks) {
        log.info("[添加消息，conversationId: {}, role: {}]", conversationId, role);

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

        log.info("[消息添加成功，messageId: {}]", message.getId());
    }

    public List<Message> getConversationMessages(Long conversationId) {
        log.info("[获取对话消息，conversationId: {}]", conversationId);
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId);
    }

    @Transactional
    public String chat(String sessionId, String question) {
        log.info("[收到对话请求，sessionId: {}, question: {}]", sessionId, question);

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

        // 获取 embedding（带多级缓存）
        float[] queryEmbedding = cachedEmbeddingService.embed(question);

        // 尝试语义缓存（注意：对话模式通常需要结合上下文，缓存策略更保守）
        String cachedAnswer = semanticCacheService.get(question, queryEmbedding);
        if (cachedAnswer != null && history.isEmpty()) {
            // 只有在没有历史上下文时才使用缓存（避免上下文丢失）
            log.info("[对话语义缓存命中]");
            addMessage(conversation.getId(), "assistant", cachedAnswer, null, null);
            return cachedAnswer;
        }

        // 使用父文档混合检索：向量+全文检索子块，再加载父块作为上下文
        List<ParentAwareHybridRetriever.RetrievalResult> retrievalResults =
                parentAwareHybridRetriever.retrieve(question, queryEmbedding, 10, 3);

        if (retrievalResults.isEmpty()) {
            log.warn("[未找到相关文档片段]");
            String answer = "抱歉，我在知识库中没有找到与您问题相关的信息。";
            addMessage(conversation.getId(), "assistant", answer, null, null);
            return answer;
        }

        // 构建带引用的上下文
        String context = parentAwareHybridRetriever.buildContextWithCitations(retrievalResults);

        // 记录检索到的片段信息
        String retrievedChunksJson = retrievalResults.stream()
                .map(result -> String.format(
                        "{\"parentId\":%d,\"section\":\"%s\",\"page\":\"%s\",\"score\":%.2f}",
                        result.getParentChunkId(),
                        result.getSectionTitle() != null ? result.getSectionTitle().replace("\"", "\\\"") : "",
                        result.getPageRange() != null ? result.getPageRange() : "",
                        result.getRelevanceScore()))
                .collect(Collectors.joining(","));

        log.info("[父文档检索完成] {} 个父块，开始生成回答", retrievalResults.size());

        // 打印命中的子块详情
        retrievalResults.forEach(result -> {
            log.info("========== 父块 [{}] ==========", result.getSectionTitle() != null ? result.getSectionTitle() : "未命名");
            log.info("页码: {}, 相关度: {}", result.getPageRange(),
                    String.format("%.2f", result.getRelevanceScore()));
            log.info("命中 {} 个子块:", result.getMatchedChildren().size());

            result.getMatchedChildren().forEach(child -> {
                String typeIcon = switch (child.getMatchType()) {
                    case "vector" -> "【向量】";
                    case "fulltext" -> "【全文】";
                    case "hybrid" -> "【混合】";
                    default -> "【未知】";
                };
                log.info("  {} 子块#{} (相似度: {})", typeIcon, child.getChunkIndex(),
                        String.format("%.2f", child.getMatchScore()));
                log.info("     内容: {}", child.getContent() != null ?
                        (child.getContent().length() > 200 ? child.getContent().substring(0, 200) + "..." : child.getContent())
                        : "无内容");
            });
            log.info("");
        });

        String answer = ragService.answerWithContext(question, context, history);

        addMessage(conversation.getId(), "assistant", answer, context, retrievedChunksJson);

        if (conversation.getMessageCount() == 2) {
            String title = generateTitle(question);
            conversation.setTitle(title);
            conversationRepository.save(conversation);
            log.info("[对话标题已更新: {}]", title);
        }

        String cacheKey = CacheConstants.CONVERSATION_CACHE_PREFIX + sessionId;
        redisTemplate.opsForValue().set(cacheKey, conversation.getId().toString(), 
                CacheConstants.CONVERSATION_CACHE_TTL, TimeUnit.SECONDS);


        // 5. 存入语义缓存（供后续相似问题使用）
        if(history.isEmpty()){
            semanticCacheService.put(question, queryEmbedding, answer);
            log.info("[答案已存入语义缓存]");
        }


        log.info("[对话完成]");
        return answer;
    }

    private String generateTitle(String question) {
        String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
        return title.replace("\n", " ").trim();
    }
}
