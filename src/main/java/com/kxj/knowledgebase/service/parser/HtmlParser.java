package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 文档解析器（支持 .html 和 .htm）
 */
@Slf4j
@Component
public class HtmlParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析 HTML 文档: {}]", fileName);

        try {
            // 读取输入流
            String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 使用 Jsoup 解析
            Document doc = Jsoup.parse(html);

            // 移除脚本、样式等无关内容
            doc.select("script, style, nav, footer, aside, noscript").remove();

            // 提取元数据
            Map<String, String> metadata = extractMetadata(doc);

            // 提取正文内容，按标题分段
            List<ParseResult.PageContent> pages = extractSections(doc);

            // 如果没有按标题分段，整体作为一个块
            String fullText;
            if (pages.isEmpty()) {
                fullText = doc.body() != null ? doc.body().text() : doc.text();
                if (!fullText.isEmpty()) {
                    pages.add(ParseResult.PageContent.builder()
                            .pageNumber(1)
                            .title(metadata.getOrDefault("title", fileName))
                            .text(fullText)
                            .charCount(fullText.length())
                            .build());
                }
            } else {
                StringBuilder textBuilder = new StringBuilder();
                for (ParseResult.PageContent page : pages) {
                    textBuilder.append(page.getText()).append("\n\n");
                }
                fullText = textBuilder.toString().trim();
            }

            log.info("[HTML 解析完成: {}, 共 {} 章节, {} 字符]",
                    fileName, pages.size(), fullText.length());

            return ParseResult.builder()
                    .success(true)
                    .text(fullText)
                    .pages(pages)
                    .metadata(metadata)
                    .totalPages(pages.size())
                    .build();

        } catch (IOException e) {
            log.error("[HTML 解析失败: {}]", fileName, e);
            return ParseResult.error("HTML 解析失败: " + e.getMessage());
        }
    }

    private Map<String, String> extractMetadata(Document doc) {
        Map<String, String> metadata = new HashMap<>();

        // 提取标题
        String title = doc.title();
        if (!title.isEmpty()) {
            metadata.put("title", title);
        }

        // 提取 meta 标签
        Elements metaTags = doc.select("meta");
        for (Element meta : metaTags) {
            String name = meta.attr("name");
            String content = meta.attr("content");
            if (!name.isEmpty() && !content.isEmpty()) {
                metadata.put(name, content);
            }

            String property = meta.attr("property");
            if (!property.isEmpty() && !content.isEmpty()) {
                metadata.put(property, content);
            }
        }

        return metadata;
    }

    private List<ParseResult.PageContent> extractSections(Document doc) {
        List<ParseResult.PageContent> pages = new ArrayList<>();

        // 查找所有标题元素
        Elements headers = doc.select("h1, h2, h3, h4, h5, h6");

        int sectionNum = 1;
        for (Element header : headers) {
            String title = header.text().trim();

            // 获取该标题后的内容，直到下一个标题
            StringBuilder content = new StringBuilder();
            Element sibling = header.nextElementSibling();

            while (sibling != null && !isHeader(sibling)) {
                String text = sibling.text().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
                sibling = sibling.nextElementSibling();
            }

            String sectionContent = content.toString().trim();
            if (!sectionContent.isEmpty() || !title.isEmpty()) {
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(sectionNum++)
                        .title(title)
                        .text(title + "\n" + sectionContent)
                        .charCount(title.length() + sectionContent.length())
                        .build());
            }
        }

        // 如果没有找到标题结构，尝试按段落提取
        if (pages.isEmpty()) {
            Elements paragraphs = doc.select("p, article, section, div.content, main");
            int groupSize = 5; // 每5个段落作为一个块
            StringBuilder groupContent = new StringBuilder();
            String currentTitle = null;
            int count = 0;

            for (Element elem : paragraphs) {
                String text = elem.text().trim();
                if (text.isEmpty() || text.length() < 10) {
                    continue;
                }

                if (currentTitle == null) {
                    currentTitle = text.substring(0, Math.min(50, text.length()));
                }

                groupContent.append(text).append("\n");
                count++;

                if (count >= groupSize) {
                    String content = groupContent.toString().trim();
                    pages.add(ParseResult.PageContent.builder()
                            .pageNumber(sectionNum++)
                            .title(currentTitle + "...")
                            .text(content)
                            .charCount(content.length())
                            .build());
                    groupContent.setLength(0);
                    currentTitle = null;
                    count = 0;
                }
            }

            // 添加剩余内容
            if (groupContent.length() > 0) {
                String content = groupContent.toString().trim();
                pages.add(ParseResult.PageContent.builder()
                        .pageNumber(sectionNum)
                        .title(currentTitle != null ? currentTitle : "Section " + sectionNum)
                        .text(content)
                        .charCount(content.length())
                        .build());
            }
        }

        return pages;
    }

    private boolean isHeader(Element element) {
        String tag = element.tagName().toLowerCase();
        return tag.matches("h[1-6]");
    }

    @Override
    public String getSupportedExtension() {
        return "html";
    }

    @Override
    public boolean supports(String extension) {
        return "html".equalsIgnoreCase(extension) || "htm".equalsIgnoreCase(extension);
    }
}
