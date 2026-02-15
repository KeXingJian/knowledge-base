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
}