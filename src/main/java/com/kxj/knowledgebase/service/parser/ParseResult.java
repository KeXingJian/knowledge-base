package com.kxj.knowledgebase.service.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文档解析结果
 */
@Data
@Builder
public class ParseResult {

    /**
     * 提取的纯文本内容
     */
    private String text;

    /**
     * 按页面/章节分割的文本片段
     */
    private List<PageContent> pages;

    /**
     * 文档元数据（标题、作者、创建时间等）
     */
    private Map<String, String> metadata;

    /**
     * 是否解析成功
     */
    private boolean success;

    /**
     * 错误信息（如果解析失败）
     */
    private String errorMessage;

    /**
     * 文档总页数/段数
     */
    private int totalPages;

    /**
     * 页面/章节内容
     */
    @Data
    @Builder
    public static class PageContent {
        /**
         * 页码/章节序号
         */
        private int pageNumber;

        /**
         * 页面/章节标题
         */
        private String title;

        /**
         * 页面/章节文本内容
         */
        private String text;

        /**
         * 字符数
         */
        private int charCount;
    }

    /**
     * 创建失败的解析结果
     */
    public static ParseResult error(String errorMessage) {
        return ParseResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建成功的解析结果
     */
    public static ParseResult success(String text) {
        return ParseResult.builder()
                .success(true)
                .text(text)
                .build();
    }
}
