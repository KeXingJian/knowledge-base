package com.kxj.knowledgebase.service.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SimpleTextSplitter implements TextSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 200;
    private static final int DEFAULT_OVERLAP = 20;

    private final int chunkSize;
    private final int overlap;

    public SimpleTextSplitter() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public SimpleTextSplitter(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<Chunk> split(String text) {
        log.info("[AI: 开始切分文本，总长度: {}]", text.length());
        List<Chunk> chunks = new ArrayList<>();

        if (text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        int index = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                end = findBestSplitPoint(text, start, end);
            }

            int chunkLength = end - start;
            if (chunkLength > 0) {
                String chunkContent = text.substring(start, end).trim();
                if (!chunkContent.isEmpty()) {
                    int tokenCount = estimateTokenCount(chunkContent);
                    chunks.add(new Chunk(chunkContent, index++, tokenCount));
                }
            }

            start = end - overlap;
            if (start < 0) {
                start = end;
            }
        }

        log.info("[AI: 文本切分完成，共生成 {} 个片段]", chunks.size());
        return chunks;
    }

    private int findBestSplitPoint(String text, int start, int end) {
        int searchStart = Math.max(start, end - 50);
        
        for (int i = end - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '\n') {
                return i + 1;
            }
        }

        return end;
    }

    private int estimateTokenCount(String text) {
        return text.length() / 3;
    }
}