package com.kxj.knowledgebase.service.storage;

import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final DocumentChunkRepository chunkRepository;

    @Transactional
    public void saveChunks(List<DocumentChunk> chunks) {
        log.info("[开始保存 {} 个文档片段到向量数据库]", chunks.size());
        chunkRepository.saveAll(chunks);
        log.info("[文档片段保存完成]");
    }

    @Transactional
    public void deleteChunksByDocumentId(Long documentId) {
        log.info("[开始删除文档 {} 的所有片段]", documentId);
        chunkRepository.deleteByDocumentId(documentId);
        log.info("[文档片段删除完成]");
    }

}