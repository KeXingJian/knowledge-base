package com.kxj.knowledgebase.repository;

import com.kxj.knowledgebase.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    void deleteByDocumentId(Long documentId);

    @Query(value = "SELECT * FROM document_chunk ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findNearestNeighbors(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query(value = "SELECT * FROM fulltext_search_chunks(:query, :limit)", nativeQuery = true)
    List<FullTextSearchResult> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    // ========== 父文档检索支持 ==========

    /**
     * 根据ID批量查询（保持顺序）
     */
    @Query("SELECT c FROM DocumentChunk c WHERE c.id IN :ids ORDER BY c.id")
    List<DocumentChunk> findAllByIdOrderById(@Param("ids") List<Long> ids);

    /**
     * 查询文档的所有子块（用于父块内容重建）
     */
    List<DocumentChunk> findByParentChunkIdOrderByChunkIndexAsc(Long parentChunkId);

    /**
     * 查询文档的所有父块
     */
    List<DocumentChunk> findByDocumentIdAndChunkLevel(Long documentId, Integer chunkLevel);

    /**
     * 向量检索：只检索子块（chunkLevel = 1）
     */
    @Query(value = "SELECT * FROM document_chunk WHERE chunk_level = 1 ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findNearestChildChunks(@Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * 指定文档内的向量检索（子块）
     */
    @Query(value = "SELECT * FROM document_chunk WHERE chunk_level = 1 AND document_id = :docId ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findNearestChildChunksByDocument(@Param("embedding") String embedding, @Param("docId") Long documentId, @Param("limit") int limit);

}