-- 启用 PGVector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建必要的 schema（如果不存在）
CREATE SCHEMA IF NOT EXISTS public;

-- 创建文档表
CREATE TABLE IF NOT EXISTS document (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(1000) NOT NULL,
    chunk_count INTEGER NOT NULL,
    upload_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    processed BOOLEAN NOT NULL,
    error_message VARCHAR(500)
);

-- 创建文档片段表
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    metadata VARCHAR(500) NOT NULL,
    token_count INTEGER NOT NULL
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_document_id ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_index ON document_chunk(chunk_index);
