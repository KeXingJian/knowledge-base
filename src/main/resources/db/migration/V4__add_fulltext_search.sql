-- Flyway迁移脚本V4：添加全文搜索支持
-- 为document_chunk表添加全文搜索功能，优化关键词检索性能

-- 添加全文搜索向量列
ALTER TABLE document_chunk 
ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- 创建触发器函数：自动更新tsvector列
CREATE OR REPLACE FUNCTION document_chunk_content_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器：在插入或更新时自动更新tsvector
DROP TRIGGER IF EXISTS tsvector_update ON document_chunk;
CREATE TRIGGER tsvector_update 
BEFORE INSERT OR UPDATE ON document_chunk
FOR EACH ROW 
EXECUTE FUNCTION document_chunk_content_tsv_trigger();

-- 为现有数据更新tsvector列
UPDATE document_chunk 
SET content_tsv = to_tsvector('simple', content) 
WHERE content_tsv IS NULL;

-- 创建GIN索引加速全文搜索
CREATE INDEX IF NOT EXISTS idx_content_tsv ON document_chunk 
USING GIN (content_tsv);

-- 创建全文搜索函数
DROP FUNCTION IF EXISTS fulltext_search_chunks(text, integer);

CREATE OR REPLACE FUNCTION fulltext_search_chunks(
    search_query text,
    limit_count integer DEFAULT 10
)
RETURNS TABLE (
    chunk_id bigint,
    document_id bigint,
    chunk_index integer,
    content text,
    rank real,
    create_time timestamp
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        dc.id as chunk_id,
        dc.document_id,
        dc.chunk_index,
        dc.content,
        ts_rank(dc.content_tsv, plainto_tsquery('simple', search_query)) as rank,
        dc.create_time
    FROM document_chunk dc
    WHERE dc.content_tsv @@ plainto_tsquery('simple', search_query)
    ORDER BY rank DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- 创建混合搜索函数（结合全文搜索和向量搜索）
CREATE OR REPLACE FUNCTION hybrid_search_chunks(
    search_query text,
    query_embedding vector,
    limit_count integer DEFAULT 10,
    text_weight numeric DEFAULT 0.4,
    vector_weight numeric DEFAULT 0.6
)
RETURNS TABLE (
    chunk_id bigint,
    document_id bigint,
    chunk_index integer,
    content text,
    text_rank double precision,
    vector_similarity double precision,
    combined_score double precision,
    create_time timestamp
) AS $$
BEGIN
    RETURN QUERY
    WITH 
    text_results AS (
        SELECT 
            dc.id,
            ts_rank(dc.content_tsv, plainto_tsquery('simple', search_query)) as text_rank
        FROM document_chunk dc
        WHERE dc.content_tsv @@ plainto_tsquery('simple', search_query)
    ),
    vector_results AS (
        SELECT 
            dc.id,
            1 - (dc.embedding <=> query_embedding) as vector_similarity
        FROM document_chunk dc
    )
    SELECT 
        dc.id as chunk_id,
        dc.document_id,
        dc.chunk_index,
        dc.content,
        COALESCE(tr.text_rank, 0) as text_rank,
        COALESCE(vr.vector_similarity, 0) as vector_similarity,
        COALESCE(tr.text_rank, 0) * text_weight + COALESCE(vr.vector_similarity, 0) * vector_weight as combined_score,
        dc.create_time
    FROM document_chunk dc
    LEFT JOIN text_results tr ON dc.id = tr.id
    LEFT JOIN vector_results vr ON dc.id = vr.id
    WHERE tr.text_rank IS NOT NULL OR vr.vector_similarity > 0.5
    ORDER BY combined_score DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- 添加注释
COMMENT ON COLUMN document_chunk.content_tsv IS '全文搜索向量列，由触发器自动维护';
COMMENT ON FUNCTION document_chunk_content_tsv_trigger IS '自动更新tsvector列的触发器函数';
COMMENT ON FUNCTION fulltext_search_chunks IS '全文搜索函数';
COMMENT ON FUNCTION hybrid_search_chunks IS '混合搜索函数（结合全文搜索和向量搜索）';
