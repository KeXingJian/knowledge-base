package com.kxj.knowledgebase.service;

import com.kxj.knowledgebase.dto.ChunkMetadata;
import com.kxj.knowledgebase.entity.Document;
import com.kxj.knowledgebase.entity.DocumentChunk;
import com.kxj.knowledgebase.service.embedding.EmbeddingService;
import com.kxj.knowledgebase.service.parser.ParseResult;
import com.kxj.knowledgebase.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增强型文档切分服务
 * 生成丰富的元数据用于 RAG 检索增强
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedChunkService {

    private final EmbeddingService embeddingService;

    /**
     * 从解析结果创建增强的 chunks
     *
     * @param parseResult 文档解析结果
     * @param document    文档实体
     * @param chunkSize   目标块大小
     * @param overlapRatio 重叠比例
     * @return 增强的 DocumentChunk 列表
     */
    public List<DocumentChunk> createEnhancedChunks(
            ParseResult parseResult,
            Document document,
            int chunkSize,
            double overlapRatio) {

        String fullText = parseResult.getText();
        if (fullText == null || fullText.trim().isEmpty()) {
            log.warn("[文档内容为空: {}]", document.getFileName());
            return List.of();
        }

        // 获取文档结构信息
        List<ParseResult.PageContent> pages = parseResult.getPages();
        String documentTitle = parseResult.getMetadata() != null
                ? parseResult.getMetadata().getOrDefault("title", document.getFileName())
                : document.getFileName();

        // 智能切分
        List<TextSegment> segments = splitWithStructure(
                fullText, pages, chunkSize, overlapRatio
        );

        int totalChunks = segments.size();
        log.info("[文档切分完成: {}, 共 {} 个片段]", document.getFileName(), totalChunks);

        // 构建 chunks 并生成元数据
        List<DocumentChunk> chunks = new ArrayList<>();
        DocumentChunk prevChunk = null;

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);

            // 生成元数据
            ChunkMetadata metadata = buildChunkMetadata(
                    segment, i, totalChunks, documentTitle, pages
            );

            // 计算摘要（前100字符）
            String summary = generateSummary(segment.text());
            metadata.setSummary(summary);

            // 创建 chunk
            DocumentChunk chunk = createChunk(
                    document, segment, metadata, prevChunk
            );

            // 建立前后关联
            if (prevChunk != null) {
                prevChunk.setNextChunkId(chunk.getId());
                chunk.setPrevChunkId(prevChunk.getId());
            }

            chunks.add(chunk);
            prevChunk = chunk;
        }

        return chunks;
    }

    /**
     * 结构化切分：结合文档原始结构
     */
    private List<TextSegment> splitWithStructure(
            String fullText,
            List<ParseResult.PageContent> pages,
            int chunkSize,
            double overlapRatio) {

        List<TextSegment> segments = new ArrayList<>();
        int overlapSize = (int) (chunkSize * overlapRatio);

        // 如果有页面结构，优先按页面边界切分
        if (pages != null && !pages.isEmpty()) {
            for (ParseResult.PageContent page : pages) {
                String pageText = page.getText();
                String pageTitle = page.getTitle();
                int pageNum = page.getPageNumber();

                // 如果页面内容超过 chunkSize，进一步切分
                if (pageText.length() <= chunkSize) {
                    segments.add(new TextSegment(
                            pageText,
                            pageNum,
                            pageNum,
                            pageTitle,
                            List.of(pageTitle),
                            pageNum
                    ));
                } else {
                    segments.addAll(splitPageContent(
                            pageText, pageNum, pageTitle, chunkSize, overlapSize
                    ));
                }
            }
        } else {
            // 无页面结构，使用智能切分
            segments.addAll(splitBySentences(fullText, chunkSize, overlapSize));
        }

        return segments;
    }

    /**
     * 切分页内容
     */
    private List<TextSegment> splitPageContent(
            String pageText, int pageNum, String pageTitle,
            int chunkSize, int overlapSize) {

        List<TextSegment> segments = new ArrayList<>();
        List<String> sentences = splitIntoSentences(pageText);

        StringBuilder currentChunk = new StringBuilder();
        List<String> overlapBuffer = new ArrayList<>();

        for (String sentence : sentences) {
            if (currentChunk.isEmpty() && !overlapBuffer.isEmpty()) {
                for (String s : overlapBuffer) {
                    currentChunk.append(s);
                }
                overlapBuffer.clear();
            }

            currentChunk.append(sentence);

            if (currentChunk.length() >= chunkSize) {
                segments.add(new TextSegment(
                        currentChunk.toString().trim(),
                        pageNum,
                        pageNum,
                        pageTitle,
                        List.of(pageTitle),
                        pageNum
                ));

                // 准备重叠
                String chunkText = currentChunk.toString();
                overlapBuffer = extractOverlapSentences(chunkText, overlapSize);
                currentChunk.setLength(0);
            }
        }

        if (!currentChunk.isEmpty()) {
            segments.add(new TextSegment(
                    currentChunk.toString().trim(),
                    pageNum,
                    pageNum,
                    pageTitle,
                    List.of(pageTitle),
                    pageNum
            ));
        }

        return segments;
    }

    /**
     * 按句子切分
     */
    private List<TextSegment> splitBySentences(
            String text, int chunkSize, int overlapSize) {

        List<TextSegment> segments = new ArrayList<>();
        List<String> sentences = splitIntoSentences(text);

        StringBuilder currentChunk = new StringBuilder();
        List<String> overlapBuffer = new ArrayList<>();
        int segmentNum = 1;

        for (String sentence : sentences) {
            if (currentChunk.isEmpty() && !overlapBuffer.isEmpty()) {
                for (String s : overlapBuffer) {
                    currentChunk.append(s);
                }
                overlapBuffer.clear();
            }

            currentChunk.append(sentence);

            if (currentChunk.length() >= chunkSize) {
                segments.add(new TextSegment(
                        currentChunk.toString().trim(),
                        segmentNum++,
                        segmentNum - 1,
                        null,
                        List.of(),
                        -1
                ));

                String chunkText = currentChunk.toString();
                overlapBuffer = extractOverlapSentences(chunkText, overlapSize);
                currentChunk.setLength(0);
            }
        }

        if (!currentChunk.isEmpty()) {
            segments.add(new TextSegment(
                    currentChunk.toString().trim(),
                    segmentNum,
                    segmentNum,
                    null,
                    List.of(),
                    -1
            ));
        }

        return segments;
    }

    /**
     * 构建 Chunk 元数据
     */
    private ChunkMetadata buildChunkMetadata(
            TextSegment segment,
            int index,
            int total,
            String documentTitle,
            List<ParseResult.PageContent> pages) {

        String content = segment.text();

        return ChunkMetadata.builder()
                .chunkIndex(index)
                .totalChunks(total)
                .charCount(content.length())
                .tokenCount(StringUtils.estimateTokenCount(content))
                .sectionTitle(segment.sectionTitle())
                .headings(segment.headings())
                .pageNumber(segment.pageNumber() > 0 ? segment.pageNumber() : null)
                .pageRange(segment.startPage() == segment.endPage()
                        ? String.valueOf(segment.startPage())
                        : segment.startPage() + "-" + segment.endPage())
                .documentTitle(documentTitle)
                .sourceType(detectSourceType(documentTitle))
                .contentType(detectContentType(content))
                .build();
    }

    /**
     * 创建 DocumentChunk 实体
     */
    private DocumentChunk createChunk(
            Document document,
            TextSegment segment,
            ChunkMetadata metadata,
            DocumentChunk prevChunk) {

        String content = segment.text();
        float[] embedding = embeddingService.embed(content);
        String embeddingString = StringUtils.floatArrayToString(embedding);

        DocumentChunk chunk = DocumentChunk.builder()
                .documentId(document.getId())
                .chunkIndex(metadata.getChunkIndex())
                .content(content)
                .embedding(embeddingString)
                .createTime(LocalDateTime.now())
                .metadata(metadata.toJson())
                .tokenCount(metadata.getTokenCount())
                // 增强字段
                .sectionTitle(metadata.getSectionTitle())
                .pageNumber(metadata.getPageNumber())
                .pageRange(metadata.getPageRange())
                .headingsPath(metadata.getHeadings() != null
                        ? String.join(" / ", metadata.getHeadings())
                        : null)
                .contentType(metadata.getContentType())
                .summary(metadata.getSummary())
                .prevChunkId(prevChunk != null ? prevChunk.getId() : null)
                .totalChunks(metadata.getTotalChunks())
                .build();

        log.debug("[创建 Chunk {}] section={}, page={}, type={}",
                metadata.getChunkIndex(),
                metadata.getSectionTitle(),
                metadata.getPageNumber(),
                metadata.getContentType());

        return chunk;
    }

    /**
     * 生成内容摘要
     */
    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 取前200字符作为摘要
        int maxLen = Math.min(200, content.length());
        String summary = content.substring(0, maxLen).trim();

        // 如果截断了句子，尝试找到句子边界
        if (maxLen < content.length()) {
            int lastSentenceEnd = Math.max(
                    summary.lastIndexOf("。"),
                    Math.max(summary.lastIndexOf("."), summary.lastIndexOf("\n"))
            );
            if (lastSentenceEnd > maxLen * 0.5) {
                summary = summary.substring(0, lastSentenceEnd + 1);
            }
            summary += "...";
        }

        return summary;
    }

    /**
     * 检测来源类型
     */
    private String detectSourceType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "word";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "excel";
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) return "ppt";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".txt")) return "text";
        return "unknown";
    }

    /**
     * 检测内容类型
     */
    private String detectContentType(String content) {
        if (content.matches("(?s).*[{\\}].*(public|private|class|def|function).*")) {
            return "code";
        }
        if (content.matches("(?s).*[┌┐└┘│─].*") || content.contains("\t")) {
            return "table";
        }
        if (content.matches("(?s)^([•\\-*]|\\d+[.\\)]).*$")) {
            return "list";
        }
        return "text";
    }

    // ========== 工具方法 ==========

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("(?<=[。！？.!?])\\s*");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                if (!trimmed.endsWith("\n")) {
                    trimmed += " ";
                }
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> extractOverlapSentences(String text, int targetLength) {
        List<String> overlapSentences = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？.!?])\\s*");

        int currentOverlap = 0;
        for (int j = sentences.length - 1; j >= 0; j--) {
            String s = sentences[j].trim();
            if (s.isEmpty()) continue;

            if (!s.endsWith("\n")) {
                s += " ";
            }

            overlapSentences.add(0, s);
            currentOverlap += s.length();

            if (overlapSentences.size() >= 1 && currentOverlap >= targetLength) {
                break;
            }
        }
        return overlapSentences;
    }

    /**
     * 文本段落实体
     */
    private record TextSegment(
            String text,
            int startPage,
            int endPage,
            String sectionTitle,
            List<String> headings,
            int pageNumber
    ) {}
}
