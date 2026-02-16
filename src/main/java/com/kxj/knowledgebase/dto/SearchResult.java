package com.kxj.knowledgebase.dto;

import com.kxj.knowledgebase.entity.DocumentChunk;
import lombok.Data;

@Data
public class SearchResult {
    private DocumentChunk chunk;
    private double score;
    private String source;
    private Float rank;

    public SearchResult(DocumentChunk chunk, double score, String source) {
        this.chunk = chunk;
        this.score = score;
        this.source = source;
        this.rank = null;
    }

    public SearchResult(DocumentChunk chunk, double score, String source, Float rank) {
        this.chunk = chunk;
        this.score = score;
        this.source = source;
        this.rank = rank;
    }
}