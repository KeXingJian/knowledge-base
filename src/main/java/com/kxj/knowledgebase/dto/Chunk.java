package com.kxj.knowledgebase.dto;

import lombok.Data;

@Data
public class Chunk {
    private String content;
    private int index;
    private int tokenCount;
    private String metadata;

    public Chunk(String content, int index, int tokenCount) {
        this.content = content;
        this.index = index;
        this.tokenCount = tokenCount;
        this.metadata = "chunk_index=" + index + ",token_count=" + tokenCount;
    }
}