package com.kxj.knowledgebase.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 缓存失效服务
 * 当文档发生变更时，负责清除相关的缓存，确保数据一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

    private final SemanticCacheService semanticCacheService;

    /**
     * 文档上传后调用：清除所有问答缓存
     * 原因：新文档可能包含已有问题的答案，旧缓存可能过时
     */
    public void onDocumentUploaded() {
        log.info("[文档上传完成，清除问答缓存]");
        semanticCacheService.invalidateAll();
    }

    /**
     * 文档删除后调用：清除所有问答缓存
     */
    public void onDocumentDeleted() {
        log.info("[文档删除完成，清除问答缓存]");
        semanticCacheService.invalidateAll();
    }

    /**
     * 批量文档处理后调用
     */
    public void onBatchProcessingCompleted() {
        log.info("[批量文档处理完成，清除问答缓存]");
        semanticCacheService.invalidateAll();
    }
}
