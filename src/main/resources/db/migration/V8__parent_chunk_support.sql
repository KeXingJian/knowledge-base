-- 父文档检索支持：添加父块关联字段
ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS parent_chunk_id BIGINT,
    ADD COLUMN IF NOT EXISTS chunk_level INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sub_chunk_count INTEGER;

-- 创建索引优化父块查询
CREATE INDEX IF NOT EXISTS idx_parent_chunk_id ON document_chunk(parent_chunk_id);
CREATE INDEX IF NOT EXISTS idx_chunk_level ON document_chunk(chunk_level);
CREATE INDEX IF NOT EXISTS idx_document_level ON document_chunk(document_id, chunk_level);

-- 添加注释
COMMENT ON COLUMN document_chunk.parent_chunk_id IS '父块ID（子块才有，父块为null）';
COMMENT ON COLUMN document_chunk.chunk_level IS '块层级：0=父块，1=子块';
COMMENT ON COLUMN document_chunk.sub_chunk_count IS '父块包含的子块数量';
