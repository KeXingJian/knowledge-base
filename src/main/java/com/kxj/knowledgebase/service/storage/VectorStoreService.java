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
        log.info("[AI: 开始保存 {} 个文档片段到向量数据库]", chunks.size());
        chunkRepository.saveAll(chunks);
        log.info("[AI: 文档片段保存完成]");
    }

    @Transactional
    public void deleteChunksByDocumentId(Long documentId) {
        log.info("[AI: 开始删除文档 {} 的所有片段]", documentId);
        chunkRepository.deleteByDocumentId(documentId);
        log.info("[AI: 文档片段删除完成]");
    }

    public List<DocumentChunk> findNearestNeighbors(float[] embedding, int limit) {
        log.info("[AI: 开始向量检索，limit: {}]", limit);
        setIndexParameters();
        String embeddingString = floatArrayToString(embedding);
        List<DocumentChunk> chunks = chunkRepository.findNearestNeighbors(embeddingString, limit);
        log.info("[AI: 向量检索完成，找到 {} 个相关片段]", chunks.size());
        return chunks;
    }

    public List<DocumentChunk> findNearestNeighborsByDocumentId(float[] embedding, Long documentId, int limit) {
        log.info("[AI: 开始在文档 {} 中进行向量检索，limit: {}]", documentId, limit);
        setIndexParameters();
        String embeddingString = floatArrayToString(embedding);
        List<DocumentChunk> chunks = chunkRepository.findNearestNeighborsByDocumentId(embeddingString, documentId, limit);
        log.info("[AI: 向量检索完成，找到 {} 个相关片段]", chunks.size());
        return chunks;
    }

    private void setIndexParameters() {
        log.info("[AI: 设置向量索引参数]");
        chunkRepository.setIvfflatProbes(10);
        chunkRepository.setHnswEfSearch(40);
    }

    private String floatArrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}