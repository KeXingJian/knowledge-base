package com.kxj.knowledgebase.repository;

import com.kxj.knowledgebase.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    @Query(value = "SELECT * FROM document_chunk ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findNearestNeighbors(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query(value = "SELECT * FROM document_chunk WHERE document_id = :documentId ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findNearestNeighborsByDocumentId(@Param("embedding") String embedding, @Param("documentId") Long documentId, @Param("limit") int limit);

    @Query(value = "SELECT * FROM fulltext_search_chunks(:query, :limit)", nativeQuery = true)
    List<FullTextSearchResult> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    @Query(value = "SELECT * FROM document_chunk WHERE content_tsv @@ plainto_tsquery('simple', :query) ORDER BY ts_rank(content_tsv, plainto_tsquery('simple', :query)) DESC LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findByContentContaining(@Param("query") String query, @Param("limit") int limit);

    @Query(value = "SELECT * FROM document_chunk WHERE content LIKE CONCAT('%', :query, '%') LIMIT :limit", nativeQuery = true)
    List<DocumentChunk> findByContentLike(@Param("query") String query, @Param("limit") int limit);

    @Query(value = "SET ivfflat.probes = :probes", nativeQuery = true)
    void setIvfflatProbes(@Param("probes") int probes);

    @Query(value = "SET hnsw.ef_search = :efSearch", nativeQuery = true)
    void setHnswEfSearch(@Param("efSearch") int efSearch);
}