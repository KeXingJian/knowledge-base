package com.kxj.knowledgebase.service.loader;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentLoader {

    boolean supports(String fileType);

    String load(Path filePath) throws IOException;
}