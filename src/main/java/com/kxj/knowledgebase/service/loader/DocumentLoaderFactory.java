package com.kxj.knowledgebase.service.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentLoaderFactory {

    private final List<DocumentLoader> loaders;

    public DocumentLoader getLoader(String fileType) {
        log.info("[AI: 查找文件类型 {} 的加载器]", fileType);
        return loaders.stream()
            .filter(loader -> loader.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型: " + fileType));
    }
}