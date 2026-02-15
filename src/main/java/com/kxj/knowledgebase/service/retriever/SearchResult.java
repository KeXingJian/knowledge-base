package com.kxj.knowledgebase.service.retriever;

import com.kxj.knowledgebase.entity.DocumentChunk;
import lombok.Data;

@Data
public class SearchResult {
    private DocumentChunk chunk;
    private double score;
    private String source;

    public SearchResult(DocumentChunk chunk, double score, String source) {
        this.chunk = chunk;
        this.score = score;
        this.source = source;
    }
}