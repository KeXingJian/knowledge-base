package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本文档解析器（支持 .txt, .md, .json, .xml, .properties 等）
 */
@Slf4j
@Component
public class TextParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析文本文档: {}]", fileName);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            List<ParseResult.PageContent> pages = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            String fullText = content.toString().trim();

            if (!fullText.isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(1)
                        .title(fileName)
                        .text(fullText)
                        .charCount(fullText.length())
                        .build());
            }

            log.info("[文本解析完成: {}, 共 {} 字符]", fileName, fullText.length());

            return ParseResult.builder()
                    .success(true)
                    .text(fullText)
                    .pages(pages)
                    .totalPages(1)
                    .build();

        } catch (IOException e) {
            log.error("[文本解析失败: {}]", fileName, e);
            return ParseResult.error("文本解析失败: " + e.getMessage());
        }
    }

    @Override
    public String getSupportedExtension() {
        return "txt";
    }

    /**
     * 纯文本解析器支持多种扩展名
     */
    @Override
    public boolean supports(String extension) {
        if (extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        return ext.equals("txt") ||
               ext.equals("md") ||
               ext.equals("markdown") ||
               ext.equals("json") ||
               ext.equals("xml") ||
               ext.equals("properties") ||
               ext.equals("yaml") ||
               ext.equals("yml") ||
               ext.equals("csv") ||
               ext.equals("log") ||
               ext.equals("java") ||
               ext.equals("py") ||
               ext.equals("js") ||
               ext.equals("ts") ||
               ext.equals("css") ||
               ext.equals("sql") ||
               ext.equals("sh") ||
               ext.equals("bat") ||
               ext.equals("ini") ||
               ext.equals("conf") ||
               ext.equals("cfg");
    }
}
