package com.kxj.knowledgebase.repository;

import com.kxj.knowledgebase.entity.DocumentChunk;

public interface FullTextSearchResult {
    Long getChunk_id();
    Long getDocument_id();
    Integer getChunk_index();
    String getContent();
    Float getRank();
    
    default DocumentChunk toDocumentChunk() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(getChunk_id());
        chunk.setDocumentId(getDocument_id());
        chunk.setChunkIndex(getChunk_index());
        chunk.setContent(getContent());
        return chunk;
    }
}
