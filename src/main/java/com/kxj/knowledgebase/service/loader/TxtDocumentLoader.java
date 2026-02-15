package com.kxj.knowledgebase.service.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class TxtDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public String load(Path filePath) throws IOException {
        log.info("[AI: 开始加载TXT文件: {}]", filePath);
        String content = Files.readString(filePath);
        log.info("[AI: TXT文件加载完成，内容长度: {}]", content.length());
        return content;
    }
}