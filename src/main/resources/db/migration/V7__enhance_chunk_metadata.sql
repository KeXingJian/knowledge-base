-- 增强 document_chunk 表元数据字段
ALTER TABLE document_chunk
    ALTER COLUMN metadata TYPE VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS section_title VARCHAR(500),
    ADD COLUMN IF NOT EXISTS page_number INTEGER,
    ADD COLUMN IF NOT EXISTS page_range VARCHAR(50),
    ADD COLUMN IF NOT EXISTS headings_path VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(20) DEFAULT 'text',
    ADD COLUMN IF NOT EXISTS summary VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS prev_chunk_id BIGINT,
    ADD COLUMN IF NOT EXISTS next_chunk_id BIGINT,
    ADD COLUMN IF NOT EXISTS total_chunks INTEGER;

-- 创建索引优化检索
CREATE INDEX IF NOT EXISTS idx_section_title ON document_chunk(section_title);
CREATE INDEX IF NOT EXISTS idx_page_number ON document_chunk(page_number);
CREATE INDEX IF NOT EXISTS idx_content_type ON document_chunk(content_type);

-- 添加注释
COMMENT ON COLUMN document_chunk.section_title IS '所属章节标题';
COMMENT ON COLUMN document_chunk.page_number IS '页码（PDF/Office）';
COMMENT ON COLUMN document_chunk.page_range IS '页码范围：3-5';
COMMENT ON COLUMN document_chunk.headings_path IS '层级路径：第一章/第二节/概述';
COMMENT ON COLUMN document_chunk.content_type IS '内容类型：text/code/table/list';
COMMENT ON COLUMN document_chunk.summary IS '内容摘要';
COMMENT ON COLUMN document_chunk.prev_chunk_id IS '前一块ID';
COMMENT ON COLUMN document_chunk.next_chunk_id IS '后一块ID';
COMMENT ON COLUMN document_chunk.total_chunks IS '总块数';
