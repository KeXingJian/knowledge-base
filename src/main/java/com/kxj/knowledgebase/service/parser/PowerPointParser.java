package com.kxj.knowledgebase.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PowerPoint 文档解析器（支持 .pptx 和 .ppt）
 */
@Slf4j
@Component
public class PowerPointParser implements DocumentParser {

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        log.info("[开始解析 PowerPoint 文档: {}]", fileName);

        String extension = getFileExtension(fileName);

        try {
            ParseResult result;
            if ("pptx".equalsIgnoreCase(extension)) {
                result = parsePptx(inputStream, fileName);
            } else if ("ppt".equalsIgnoreCase(extension)) {
                result = parsePpt(inputStream, fileName);
            } else {
                return ParseResult.error("不支持的 PowerPoint 格式: " + extension);
            }

            log.info("[PowerPoint 解析完成: {}, 共 {} 页]", fileName, result.getTotalPages());
            return result;

        } catch (Exception e) {
            log.error("[PowerPoint 解析失败: {}]", fileName, e);
            return ParseResult.error("PowerPoint 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 .pptx 格式（Office 2007+）
     */
    private ParseResult parsePptx(InputStream inputStream, String fileName) throws IOException {
        try (XMLSlideShow slideshow = new XMLSlideShow(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            List<ParseResult.PageContent> pages = new ArrayList<>();

            int slideNum = 1;
            for (XSLFSlide slide : slideshow.getSlides()) {
                StringBuilder slideText = new StringBuilder();

                // 提取幻灯片标题
                String slideTitle = null;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            // 第一个非空文本通常作为标题
                            if (slideTitle == null && text.length() < 100) {
                                slideTitle = text;
                            }
                            slideText.append(text).append("\n");
                        }
                    }
                }

                String slideContent = slideText.toString().trim();
                if (!slideContent.isEmpty()) {
                    pages.add(ParseResult.PageContent.builder()
                            .pageNumber(slideNum)
                            .title(slideTitle != null ? slideTitle : "Slide " + slideNum)
                            .text(slideContent)
                            .charCount(slideContent.length())
                            .build());

                    fullText.append("Slide ").append(slideNum).append(": ")
                            .append(slideContent).append("\n\n");
                }
                slideNum++;
            }

            return ParseResult.builder()
                    .success(true)
                    .text(fullText.toString().trim())
                    .pages(pages)
                    .totalPages(slideshow.getSlides().size())
                    .build();
        }
    }

    /**
     * 解析 .ppt 格式（Office 97-2003）
     */
    private ParseResult parsePpt(InputStream inputStream, String fileName) throws IOException {
        try (HSLFSlideShow slideshow = new HSLFSlideShow(inputStream)) {
            StringBuilder fullText = new StringBuilder();
            List<ParseResult.PageContent> pages = new ArrayList<>();

            int slideNum = 1;
            for (HSLFSlide slide : slideshow.getSlides()) {
                StringBuilder slideText = new StringBuilder();
                String slideTitle = null;

                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            if (slideTitle == null && text.length() < 100) {
                                slideTitle = text;
                            }
                            slideText.append(text).append("\n");
                        }
                    }
                }

                String slideContent = slideText.toString().trim();
                if (!slideContent.isEmpty()) {
                    pages.add(ParseResult.PageContent.builder()
                            .pageNumber(slideNum)
                            .title(slideTitle != null ? slideTitle : "Slide " + slideNum)
                            .text(slideContent)
                            .charCount(slideContent.length())
                            .build());

                    fullText.append("Slide ").append(slideNum).append(": ")
                            .append(slideContent).append("\n\n");
                }
                slideNum++;
            }

            return ParseResult.builder()
                    .success(true)
                    .text(fullText.toString().trim())
                    .pages(pages)
                    .totalPages(slideshow.getSlides().size())
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
        return "pptx";
    }

    @Override
    public boolean supports(String extension) {
        return "pptx".equalsIgnoreCase(extension) || "ppt".equalsIgnoreCase(extension);
    }
}
