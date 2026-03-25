package com.kxj.knowledgebase.service.parser;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析器工厂
 * 根据文件扩展名返回对应的解析器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;
    private final Map<String, DocumentParser> parserMap = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("[初始化文档解析器工厂，共 {} 个解析器]", parsers.size());

        for (DocumentParser parser : parsers) {
            String extension = parser.getSupportedExtension().toLowerCase();
            parserMap.put(extension, parser);
            log.info("[注册解析器: {} -> {}]", extension, parser.getClass().getSimpleName());
        }
    }

    /**
     * 根据文件扩展名获取解析器
     *
     * @param extension 文件扩展名（不含点）
     * @return 对应的解析器，如果没有找到则返回纯文本解析器
     */
    public DocumentParser getParser(String extension) {
        if (extension == null || extension.isEmpty()) {
            log.warn("[文件扩展名为空，使用默认文本解析器]");
            return getTextParser();
        }

        String ext = extension.toLowerCase().trim();

        // 首先尝试精确匹配
        DocumentParser parser = parserMap.get(ext);
        if (parser != null) {
            return parser;
        }

        // 尝试通过 supports 方法匹配（处理一个解析器支持多种扩展名的情况）
        for (DocumentParser p : parsers) {
            if (p.supports(ext)) {
                return p;
            }
        }

        // 默认返回文本解析器
        log.debug("[未找到解析器: {}, 使用默认文本解析器]", extension);
        return getTextParser();
    }

    /**
     * 检查是否支持该文件类型
     *
     * @param extension 文件扩展名
     * @return true 如果有对应的解析器
     */
    public boolean isSupported(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }

        String ext = extension.toLowerCase().trim();

        if (parserMap.containsKey(ext)) {
            return true;
        }

        for (DocumentParser p : parsers) {
            if (p.supports(ext)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取纯文本解析器作为默认解析器
     */
    private DocumentParser getTextParser() {
        DocumentParser textParser = parserMap.get("txt");
        if (textParser == null) {
            // 如果没有找到文本解析器，返回第一个可用的解析器
            return parsers.stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("没有可用的文档解析器"));
        }
        return textParser;
    }
}
