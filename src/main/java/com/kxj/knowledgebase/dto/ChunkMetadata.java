package com.kxj.knowledgebase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chunk 增强元数据
 * 存储丰富的上下文信息用于 RAG 检索增强
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkMetadata {

    /**
     * 基础位置信息
     */
    private Integer chunkIndex;
    private Integer totalChunks;
    private Integer tokenCount;
    private Integer charCount;

    /**
     * 文档结构信息
     */
    private String sectionTitle;      // 所属章节标题
    private List<String> headings;    // 层级路径：[第一章, 第二节, 概述]
    private String parentSection;     // 父级章节
    private Integer level;            // 层级深度（0=文档级，1=章，2=节）

    /**
     * 页面/位置信息
     */
    private Integer pageNumber;       // 页码（PDF/Office）
    private Integer startPage;        // 起始页
    private Integer endPage;          // 结束页
    private String pageRange;         // 页码范围：3-5

    /**
     * 内容摘要
     */
    private String summary;           // 块内容摘要（可自动生成）
    private List<String> keywords;    // 关键词提取
    private String contentType;       // 内容类型：text/code/table/list

    /**
     * 上下文衔接
     */
    private String prevChunkSummary;  // 前一块摘要
    private String nextChunkSummary;  // 后一块摘要
    private Long prevChunkId;         // 前一块ID
    private Long nextChunkId;         // 后一块ID

    /**
     * 来源信息
     */
    private String sourceType;        // 来源：pdf/word/excel/html/txt
    private String documentTitle;     // 文档标题

    /**
     * 转换为数据库存储的 JSON 字符串
     */
    public String toJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 从 JSON 字符串解析
     */
    public static ChunkMetadata fromJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, ChunkMetadata.class);
        } catch (Exception e) {
            return ChunkMetadata.builder().build();
        }
    }

    /**
     * 构建基础元数据
     */
    public static ChunkMetadataBuilder baseBuilder(int index, int total, String content) {
        return ChunkMetadata.builder()
                .chunkIndex(index)
                .totalChunks(total)
                .charCount(content.length())
                .contentType(detectContentType(content));
    }

    /**
     * 检测内容类型
     */
    private static String detectContentType(String content) {
        if (content.matches("(?s).*(\\{|\\}|public class|def |import ).*")) {
            return "code";
        }
        if (content.matches("(?s).*┌.*┐.*|.*\\|.*\\|.*")) {
            return "table";
        }
        if (content.matches("(?s)^[•\\-\\d]\\..*")) {
            return "list";
        }
        return "text";
    }
}
