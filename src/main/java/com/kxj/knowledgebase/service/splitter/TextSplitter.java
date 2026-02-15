package com.kxj.knowledgebase.service.splitter;

import java.util.List;

public interface TextSplitter {

    List<Chunk> split(String text);
}