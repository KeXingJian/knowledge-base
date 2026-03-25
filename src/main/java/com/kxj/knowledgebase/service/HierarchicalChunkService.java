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
 * 分层切分服务
 * 父块：大粒度（章节/页），用于提供完整上下文
 * 子块：小粒度（句子边界），用于向量检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HierarchicalChunkService {

    private final EmbeddingService embeddingService;

    // 父块目标大小（字符数）
    private static final int PARENT_CHUNK_SIZE = 2000;
    // 子块目标大小（字符数）
    private static final int CHILD_CHUNK_SIZE = 300;
    // 子块重叠比例
    private static final double CHILD_OVERLAP_RATIO = 0.2;

    /**
     * 创建分层 chunks：父块 + 子块
     *
     * @param parseResult 文档解析结果
     * @param document    文档实体
     * @return 所有 chunks（父块在前，子块在后）
     */
    public List<DocumentChunk> createHierarchicalChunks(
            ParseResult parseResult,
            Document document) {

        String fullText = parseResult.getText();
        if (fullText == null || fullText.trim().isEmpty()) {
            log.warn("[文档内容为空: {}]", document.getFileName());
            return List.of();
        }

        List<ParseResult.PageContent> pages = parseResult.getPages();
        String documentTitle = parseResult.getMetadata() != null
                ? parseResult.getMetadata().getOrDefault("title", document.getFileName())
                : document.getFileName();

        log.info("[开始分层切分文档: {}, documentId={}]", document.getFileName(), document.getId());

        // 1. 创建父块（按文档结构）
        List<ParentSegment> parentSegments = createParentSegments(pages, fullText);
        log.info("[创建 {} 个父块]", parentSegments.size());

        // 2. 为每个父块创建子块
        List<DocumentChunk> allChunks = new ArrayList<>();
        int parentIndex = 0;
        int childGlobalIndex = 0;

        for (ParentSegment parentSeg : parentSegments) {
            // 创建父块
            DocumentChunk parentChunk = createParentChunk(
                    document, parentSeg, parentIndex, documentTitle, parentSegments.size()
            );
            allChunks.add(parentChunk);

            // 在父块内创建子块
            List<DocumentChunk> childChunks = createChildChunks(
                    document, parentSeg, parentChunk, childGlobalIndex, documentTitle
            );

            // 建立父子关联（用父块的临时索引，保存后会更新为真实ID）
            parentChunk.setSubChunkCount(childChunks.size());
            // 使用负数作为临时父块标识，保存后会更新
            long tempParentId = -1L * (parentIndex + 1);
            for (DocumentChunk child : childChunks) {
                child.setParentChunkId(tempParentId);
                allChunks.add(child);
            }

            childGlobalIndex += childChunks.size();
            parentIndex++;

            log.debug("[父块 {} 创建 {} 个子块]", parentIndex, childChunks.size());
        }

        // 验证所有 chunks 的 document_id
        long distinctDocIds = allChunks.stream().map(DocumentChunk::getDocumentId).distinct().count();
        log.info("[分层切分完成: {} 个父块, {} 个子块, 总计 {} 个chunks, documentId 分布: {} 个不同值]",
                parentIndex, childGlobalIndex, allChunks.size(), distinctDocIds);

        if (distinctDocIds != 1) {
            log.error("[文档ID不一致！发现 {} 个不同的 document_id]", distinctDocIds);
            allChunks.stream()
                    .collect(Collectors.groupingBy(DocumentChunk::getDocumentId, Collectors.counting()))
                    .forEach((docId, count) -> log.error("  document_id={}: {} 个chunks", docId, count));
        }

        return allChunks;
    }

    /**
     * 创建父块段（按文档结构切分）
     */
    private List<ParentSegment> createParentSegments(
            List<ParseResult.PageContent> pages,
            String fullText) {

        List<ParentSegment> segments = new ArrayList<>();

        if (pages != null && !pages.isEmpty()) {
            // 有页面结构：优先按页切分，页面过大时合并或拆分
            for (ParseResult.PageContent page : pages) {
                String pageText = page.getText();

                if (pageText.length() <= PARENT_CHUNK_SIZE * 1.5) {
                    // 页面大小合适，作为一个父块
                    segments.add(new ParentSegment(
                            pageText,
                            page.getTitle(),
                            page.getPageNumber(),
                            page.getPageNumber(),
                            List.of(page.getTitle())
                    ));
                } else {
                    // 页面过大，拆分为多个父块
                    segments.addAll(splitLargePage(pageText, page.getTitle(), page.getPageNumber()));
                }
            }
        } else {
            // 无页面结构：按大段落切分
            segments.addAll(splitByLargeParagraphs(fullText));
        }

        return segments;
    }

    /**
     * 拆分过大的页面
     */
    private List<ParentSegment> splitLargePage(String pageText, String pageTitle, int pageNum) {
        List<ParentSegment> segments = new ArrayList<>();
        List<String> paragraphs = splitByParagraphs(pageText);

        StringBuilder currentSegment = new StringBuilder();
        String currentTitle = pageTitle;
        int segmentStartPage = pageNum;

        for (String para : paragraphs) {
            if (currentSegment.length() + para.length() > PARENT_CHUNK_SIZE
                    && currentSegment.length() > 0) {
                // 保存当前段
                segments.add(new ParentSegment(
                        currentSegment.toString().trim(),
                        currentTitle,
                        segmentStartPage,
                        pageNum,
                        List.of(pageTitle)
                ));
                currentSegment.setLength(0);
            }
            currentSegment.append(para).append("\n\n");
        }

        // 保存最后一段
        if (currentSegment.length() > 0) {
            segments.add(new ParentSegment(
                    currentSegment.toString().trim(),
                    currentTitle,
                    segmentStartPage,
                    pageNum,
                    List.of(pageTitle)
            ));
        }

        return segments;
    }

    /**
     * 按大段落切分（无页面结构时使用）
     */
    private List<ParentSegment> splitByLargeParagraphs(String text) {
        List<ParentSegment> segments = new ArrayList<>();
        List<String> paragraphs = splitByParagraphs(text);

        StringBuilder currentSegment = new StringBuilder();
        String currentTitle = null;
        int segmentIndex = 0;

        for (String para : paragraphs) {
            // 检测是否是标题行（短且以特定词开头）
            if (isHeadingLine(para)) {
                // 保存之前的段
                if (currentSegment.length() > 0) {
                    segments.add(new ParentSegment(
                            currentSegment.toString().trim(),
                            currentTitle,
                            -1, -1,
                            currentTitle != null ? List.of(currentTitle) : List.of()
                    ));
                    currentSegment.setLength(0);
                }
                currentTitle = para.trim();
            }

            if (currentSegment.length() + para.length() > PARENT_CHUNK_SIZE
                    && currentSegment.length() > 0) {
                segments.add(new ParentSegment(
                        currentSegment.toString().trim(),
                        currentTitle,
                        -1, -1,
                        currentTitle != null ? List.of(currentTitle) : List.of()
                ));
                currentSegment.setLength(0);
            }

            currentSegment.append(para).append("\n\n");
        }

        if (currentSegment.length() > 0) {
            segments.add(new ParentSegment(
                    currentSegment.toString().trim(),
                    currentTitle,
                    -1, -1,
                    currentTitle != null ? List.of(currentTitle) : List.of()
            ));
        }

        return segments;
    }

    /**
     * 创建父块实体
     */
    private DocumentChunk createParentChunk(
            Document document,
            ParentSegment segment,
            int parentIndex,
            String documentTitle,
            int totalParents) {

        String content = segment.text();

        // 父块不向量化（节省存储），只有子块有向量用于检索
        // embedding 字段在数据库中是 NOT NULL，所以设置为空字符串或特殊标记
        String emptyEmbedding = StringUtils.floatArrayToString(new float[768]);

        ChunkMetadata metadata = ChunkMetadata.builder()
                .chunkIndex(parentIndex)
                .totalChunks(totalParents)
                .sectionTitle(segment.sectionTitle())
                .charCount(content.length())
                .tokenCount(StringUtils.estimateTokenCount(content))
                .pageNumber(segment.startPage() > 0 ? segment.startPage() : null)
                .pageRange(formatPageRange(segment.startPage(), segment.endPage()))
                .documentTitle(documentTitle)
                .contentType(detectContentType(content))
                .build();

        return DocumentChunk.builder()
                .documentId(document.getId())
                .chunkIndex(parentIndex)
                .content(content)
                .embedding(emptyEmbedding)  // 父块无有效向量
                .createTime(LocalDateTime.now())
                .metadata(metadata.toJson())
                .tokenCount(metadata.getTokenCount())
                // 父块标识
                .chunkLevel(0)
                .parentChunkId(null)
                // 增强字段
                .sectionTitle(segment.sectionTitle())
                .pageNumber(metadata.getPageNumber())
                .pageRange(metadata.getPageRange())
                .headingsPath(String.join(" / ", segment.headings()))
                .contentType(metadata.getContentType())
                .summary(generateSummary(content))
                .totalChunks(totalParents)
                .build();
    }

    /**
     * 为父块创建子块
     */
    private List<DocumentChunk> createChildChunks(
            Document document,
            ParentSegment parentSeg,
            DocumentChunk parentChunk,
            int startChildIndex,
            String documentTitle) {

        List<DocumentChunk> childChunks = new ArrayList<>();
        String parentText = parentSeg.text();

        // 在父块内按句子边界切分子块
        List<String> childTexts = splitIntoChildChunks(
                parentText, CHILD_CHUNK_SIZE, CHILD_OVERLAP_RATIO
        );

        int childLocalIndex = 0;
        for (String childText : childTexts) {
            int globalIndex = startChildIndex + childLocalIndex;

            float[] embedding = embeddingService.embed(childText);
            String embeddingString = StringUtils.floatArrayToString(embedding);

            ChunkMetadata metadata = ChunkMetadata.builder()
                    .chunkIndex(globalIndex)
                    .totalChunks(-1) // 子块总数在父块中记录
                    .sectionTitle(parentSeg.sectionTitle())
                    .charCount(childText.length())
                    .tokenCount(StringUtils.estimateTokenCount(childText))
                    .pageNumber(parentChunk.getPageNumber())
                    .pageRange(parentChunk.getPageRange())
                    .documentTitle(documentTitle)
                    .contentType(detectContentType(childText))
                    .build();

            DocumentChunk childChunk = DocumentChunk.builder()
                    .documentId(document.getId())
                    .chunkIndex(globalIndex)
                    .content(childText)
                    .embedding(embeddingString)
                    .createTime(LocalDateTime.now())
                    .metadata(metadata.toJson())
                    .tokenCount(metadata.getTokenCount())
                    // 子块标识
                    .chunkLevel(1)
                    .parentChunkId(parentChunk.getId())
                    // 增强字段
                    .sectionTitle(parentSeg.sectionTitle())
                    .pageNumber(parentChunk.getPageNumber())
                    .pageRange(parentChunk.getPageRange())
                    .headingsPath(parentChunk.getHeadingsPath())
                    .contentType(metadata.getContentType())
                    .summary(generateSummary(childText))
                    .build();

            childChunks.add(childChunk);
            childLocalIndex++;
        }

        return childChunks;
    }

    /**
     * 切分子块（句子边界 + 重叠）
     */
    private List<String> splitIntoChildChunks(String text, int chunkSize, double overlapRatio) {
        List<String> chunks = new ArrayList<>();
        List<String> sentences = splitIntoSentences(text);

        if (sentences.isEmpty()) {
            return chunks;
        }

        StringBuilder currentChunk = new StringBuilder();
        List<String> overlapBuffer = new ArrayList<>();
        int overlapTarget = (int) (chunkSize * overlapRatio);

        for (String sentence : sentences) {
            if (currentChunk.isEmpty() && !overlapBuffer.isEmpty()) {
                for (String s : overlapBuffer) {
                    currentChunk.append(s);
                }
                overlapBuffer.clear();
            }

            currentChunk.append(sentence);

            if (currentChunk.length() >= chunkSize) {
                chunks.add(currentChunk.toString().trim());

                String chunkText = currentChunk.toString();
                overlapBuffer = extractOverlapSentences(chunkText, overlapTarget);
                currentChunk.setLength(0);
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    // ========== 工具方法 ==========

    private List<String> splitByParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] parts = text.split("\n\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private boolean isHeadingLine(String line) {
        String trimmed = line.trim();
        if (trimmed.length() > 100) return false;
        // 匹配标题特征：数字开头、特定关键词、短行
        return trimmed.matches("^[第\\d一二三四五六七八九十]+[章节篇].*") ||
               trimmed.matches("^\\d+[.、].*") ||
               trimmed.matches("^[【\\[][^】\\]]+[】\\]].*");
    }

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

    private String formatPageRange(int start, int end) {
        if (start <= 0 || end <= 0) return null;
        return start == end ? String.valueOf(start) : start + "-" + end;
    }

    private String detectContentType(String content) {
        if (content.matches("(?s).*[{\\}].*(public|private|class|def|function).*")) {
            return "code";
        }
        if (content.matches("(?s).*[┌┐└┘│─].*") || content.contains("\t")) {
            return "table";
        }
        return "text";
    }

    private String generateSummary(String content) {
        if (content == null || content.isEmpty()) return "";
        int maxLen = Math.min(200, content.length());
        String summary = content.substring(0, maxLen).trim();

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
     * 父块段记录
     */
    private record ParentSegment(
            String text,
            String sectionTitle,
            int startPage,
            int endPage,
            List<String> headings
    ) {}
}
