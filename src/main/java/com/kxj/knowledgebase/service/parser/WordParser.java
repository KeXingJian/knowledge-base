package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word 文档解析器（支持 .docx 和 .doc）
 */
@Slf4j
@Component
public class WordParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析 Word 文档: {}]", fileName);

        String extension = getFileExtension(fileName);

        try {
            ParseResult result;
            if ("docx".equalsIgnoreCase(extension)) {
                result = parseDocx(inputStream, fileName);
            } else if ("doc".equalsIgnoreCase(extension)) {
                result = parseDoc(inputStream, fileName);
            } else {
                return ParseResult.error("不支持的 Word 格式: " + extension);
            }

            log.info("[Word 解析完成: {}, 共 {} 字符]", fileName,
                    result.getText() != null ? result.getText().length() : 0);
            return result;

        } catch (Exception e) {
            log.error("[Word 解析失败: {}]", fileName, e);
            return ParseResult.error("Word 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 .docx 格式（Office 2007+）
     */
    private ParseResult parseDocx(InputStream inputStream, String fileName) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder textBuilder = new StringBuilder();
            List<ParseResult.PageContent> pages = new ArrayList<>();
            Map<String, String> metadata = new HashMap<>();

            // 提取元数据
            var coreProps = document.getProperties().getCoreProperties();
            if (coreProps.getTitle() != null) {
                metadata.put("title", coreProps.getTitle());
            }
            if (coreProps.getCreator() != null) {
                metadata.put("author", coreProps.getCreator());
            }

            // 按段落提取文本，同时识别标题层级
            int sectionNumber = 1;
            StringBuilder sectionBuilder = new StringBuilder();
            String lastHeading = null;

            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String paragraphText = paragraph.getText().trim();
                    if (paragraphText.isEmpty()) {
                        continue;
                    }

                    // 检测标题样式
                    String style = paragraph.getStyle();
                    boolean isHeading = style != null && (style.startsWith("Heading") || style.startsWith("标题"));

                    // 如果遇到新标题且已有内容，保存当前章节
                    if (isHeading && sectionBuilder.length() > 0) {
                        pages.add(ParseResult.PageContent.builder()
                                .pageNumber(sectionNumber++)
                                .title(lastHeading)
                                .text(sectionBuilder.toString().trim())
                                .charCount(sectionBuilder.length())
                                .build());
                        sectionBuilder.setLength(0);
                        lastHeading = paragraphText;
                    }

                    if (isHeading) {
                        lastHeading = paragraphText;
                    }

                    sectionBuilder.append(paragraphText).append("\n");
                    textBuilder.append(paragraphText).append("\n");
                } else if (element instanceof XWPFTable table) {
                    // 处理表格内容
                    for (var row : table.getRows()) {
                        StringBuilder rowText = new StringBuilder();
                        for (var cell : row.getTableCells()) {
                            rowText.append(cell.getText()).append("\t");
                        }
                        String rowStr = rowText.toString().trim();
                        if (!rowStr.isEmpty()) {
                            sectionBuilder.append(rowStr).append("\n");
                            textBuilder.append(rowStr).append("\n");
                        }
                    }
                }
            }

            // 保存最后一个章节
            if (sectionBuilder.length() > 0) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(sectionNumber)
                        .title(lastHeading)
                        .text(sectionBuilder.toString().trim())
                        .charCount(sectionBuilder.length())
                        .build());
            }

            return ParseResult.builder()
                    .success(true)
                    .text(textBuilder.toString().trim())
                    .pages(pages)
                    .metadata(metadata)
                    .totalPages(pages.size())
                    .build();
        }
    }

    /**
     * 解析 .doc 格式（Office 97-2003）
     */
    private ParseResult parseDoc(InputStream inputStream, String fileName) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {

            String text = extractor.getText();
            List<ParseResult.PageContent> pages = new ArrayList<>();

            // .doc 格式难以按段落精确分割，整体作为一个块
            if (text != null && !text.trim().isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(1)
                        .text(text.trim())
                        .charCount(text.length())
                        .build());
            }

            return ParseResult.builder()
                    .success(true)
                    .text(text != null ? text.trim() : "")
                    .pages(pages)
                    .totalPages(1)
                    .build();
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    @Override
    public String getSupportedExtension() {
        return "docx"; // 主要支持 docx，但 parse 方法内部也处理 doc
    }

    /**
     * 是否支持该文件类型
     */
    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension) || "doc".equalsIgnoreCase(extension);
    }
}
