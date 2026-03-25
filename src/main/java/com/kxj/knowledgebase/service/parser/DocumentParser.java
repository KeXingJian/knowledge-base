package com.kxj.knowledgebase.service.parser;

import java.io.InputStream;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 解析文档内容
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于日志和元数据）
     * @return 解析结果
     */
    ParseResult parse(InputStream inputStream, String fileName);

    /**
     * 获取支持的文件扩展名
     *
     * @return 文件扩展名（不含点，如 "pdf", "docx"）
     */
    String getSupportedExtension();

    /**
     * 是否支持该文件类型
     *
     * @param extension 文件扩展名
     * @return true 如果支持
     */
    default boolean supports(String extension) {
        return getSupportedExtension().equalsIgnoreCase(extension);
    }
}
