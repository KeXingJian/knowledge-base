package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 文档解析器
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析 PDF 文档: {}]", fileName);

        try {
            // PDFBox 3.0 需要 byte[] 或 File
            byte[] pdfBytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper textStripper = new PDFTextStripper();

                // 提取元数据
                Map<String, String> metadata = extractMetadata(document);

                // 按页提取文本
                List<ParseResult.PageContent> pages = new ArrayList<>();
                int totalPages = document.getNumberOfPages();

                for (int i = 1; i <= totalPages; i++) {
                    textStripper.setStartPage(i);
                    textStripper.setEndPage(i);
                    String pageText = textStripper.getText(document);

                    if (pageText != null && !pageText.trim().isEmpty()) {
                        pages.add(ParseResult.PageContent.builder()
                                .pageNumber(i)
                                .text(pageText.trim())
                                .charCount(pageText.length())
                                .build());
                    }
                }

                // 合并所有页面文本
                StringBuilder fullText = new StringBuilder();
                for (ParseResult.PageContent page : pages) {
                    fullText.append(page.getText()).append("\n\n");
                }

                log.info("[PDF 解析完成: {}, 共 {} 页, 提取 {} 字符]",
                        fileName, totalPages, fullText.length());

                return ParseResult.builder()
                        .success(true)
                        .text(fullText.toString().trim())
                        .pages(pages)
                        .metadata(metadata)
                        .totalPages(totalPages)
                        .build();
            }
        } catch (IOException e) {
            log.error("[PDF 解析失败: {}]", fileName, e);
            return ParseResult.error("PDF 解析失败: " + e.getMessage());
        }
    }

    private Map<String, String> extractMetadata(PDDocument document) {
        Map<String, String> metadata = new HashMap<>();
        try {
            var info = document.getDocumentInformation();
            if (info.getTitle() != null) {
                metadata.put("title", info.getTitle());
            }
            if (info.getAuthor() != null) {
                metadata.put("author", info.getAuthor());
            }
            if (info.getSubject() != null) {
                metadata.put("subject", info.getSubject());
            }
            if (info.getKeywords() != null) {
                metadata.put("keywords", info.getKeywords());
            }
            if (info.getCreator() != null) {
                metadata.put("creator", info.getCreator());
            }
        } catch (Exception e) {
            log.warn("[提取 PDF 元数据失败]", e);
        }
        return metadata;
    }

    @Override
    public String getSupportedExtension() {
        return "pdf";
    }
}
