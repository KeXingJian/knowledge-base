-- 启用向量扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建文档片段表
CREATE TABLE IF NOT EXISTS document_segments (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 text TEXT NOT NULL,
                                                 embedding vector(768),  -- nomic-embed-text 输出768维向量
                                                 metadata JSONB,
                                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以加速相似度检索
CREATE INDEX IF NOT EXISTS idx_document_segments_embedding
    ON document_segments
        USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 创建元数据查询索引
CREATE INDEX IF NOT EXISTS idx_document_segments_metadata
    ON document_segments
        USING gin (metadata);

-- 创建全文搜索索引（用于BM25检索）
CREATE INDEX IF NOT EXISTS idx_document_segments_text
    ON document_segments
        USING gin (to_tsvector('simple', text));