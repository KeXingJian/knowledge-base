-- Flyway迁移脚本V3：创建监控视图和辅助函数
-- 添加用于监控和维护向量索引的视图和函数

-- 创建向量索引监控视图
CREATE OR REPLACE VIEW v_vector_index_status AS
SELECT 
    s.schemaname,
    s.relname as tablename,
    s.indexrelname as indexname,
    pg_size_pretty(pg_relation_size(s.indexrelid)) as index_size,
    s.idx_scan as index_scans,
    s.idx_tup_read as tuples_read,
    s.idx_tup_fetch as tuples_fetched,
    CASE 
        WHEN s.idx_scan = 0 THEN 'UNUSED'
        WHEN s.idx_scan < 100 THEN 'LOW_USAGE'
        ELSE 'ACTIVE'
    END as usage_status
FROM pg_stat_user_indexes s
WHERE s.schemaname = 'public' 
  AND s.relname = 'document_chunk'
  AND s.indexrelname LIKE '%embedding%'
ORDER BY s.idx_scan DESC;

-- 创建文档统计视图
CREATE OR REPLACE VIEW v_document_statistics AS
SELECT 
    d.id as document_id,
    d.file_name,
    d.file_type,
    d.file_size,
    d.chunk_count,
    d.upload_time,
    d.update_time,
    d.processed,
    COUNT(dc.id) as actual_chunk_count,
    SUM(dc.token_count) as total_tokens,
    AVG(dc.token_count) as avg_tokens_per_chunk,
    MAX(dc.token_count) as max_tokens_per_chunk,
    MIN(dc.token_count) as min_tokens_per_chunk
FROM document d
LEFT JOIN document_chunk dc ON d.id = dc.document_id
GROUP BY d.id, d.file_name, d.file_type, d.file_size, d.chunk_count, 
         d.upload_time, d.update_time, d.processed
ORDER BY d.upload_time DESC;

-- 创建向量相似度搜索函数
CREATE OR REPLACE FUNCTION search_similar_chunks(
    query_embedding vector,
    limit_count integer DEFAULT 10
)
RETURNS TABLE (
    chunk_id bigint,
    document_id bigint,
    chunk_index integer,
    content text,
    similarity double precision,
    create_time timestamp
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        dc.id as chunk_id,
        dc.document_id,
        dc.chunk_index,
        dc.content,
        1 - (dc.embedding <=> query_embedding) as similarity,
        dc.create_time
    FROM document_chunk dc
    ORDER BY dc.embedding <=> query_embedding
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- 创建文档内向量相似度搜索函数
CREATE OR REPLACE FUNCTION search_similar_chunks_in_document(
    doc_id bigint,
    query_embedding vector,
    limit_count integer DEFAULT 10
)
RETURNS TABLE (
    chunk_id bigint,
    chunk_index integer,
    content text,
    similarity double precision,
    create_time timestamp
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        dc.id as chunk_id,
        dc.chunk_index,
        dc.content,
        1 - (dc.embedding <=> query_embedding) as similarity,
        dc.create_time
    FROM document_chunk dc
    WHERE dc.document_id = doc_id
    ORDER BY dc.embedding <=> query_embedding
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- 创建索引性能分析函数
CREATE OR REPLACE FUNCTION analyze_index_performance()
RETURNS TABLE (
    index_name text,
    index_type text,
    index_size text,
    scans bigint,
    tuples_read bigint,
    tuples_fetched bigint,
    usage_rate numeric
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.relname as index_name,
        am.amname as index_type,
        pg_size_pretty(pg_relation_size(i.oid)) as index_size,
        s.idx_scan as scans,
        s.idx_tup_read as tuples_read,
        s.idx_tup_fetch as tuples_fetched,
        CASE 
            WHEN s.seq_scan + s.idx_scan = 0 THEN 0
            ELSE ROUND(s.idx_scan::numeric / (s.seq_scan + s.idx_scan) * 100, 2)
        END as usage_rate
    FROM pg_stat_user_indexes s
    JOIN pg_class i ON s.indexrelid = i.oid
    JOIN pg_class t ON s.indrelid = t.oid
    JOIN pg_am am ON i.relam = am.oid
    WHERE s.schemaname = 'public' 
      AND s.relname = 'document_chunk'
      AND am.amname IN ('ivfflat', 'hnsw')
    ORDER BY s.idx_scan DESC;
END;
$$ LANGUAGE plpgsql;

-- 创建向量分布统计函数
CREATE OR REPLACE FUNCTION get_vector_distribution_stats()
RETURNS TABLE (
    total_chunks bigint,
    unique_documents bigint,
    avg_tokens numeric,
    max_tokens integer,
    min_tokens integer
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*) as total_chunks,
        COUNT(DISTINCT document_id) as unique_documents,
        AVG(token_count) as avg_tokens,
        MAX(token_count) as max_tokens,
        MIN(token_count) as min_tokens
    FROM document_chunk;
END;
$$ LANGUAGE plpgsql;

-- 创建索引碎片化检查函数
CREATE OR REPLACE FUNCTION check_index_fragmentation()
RETURNS TABLE (
    index_name text,
    index_size text,
    dead_tuples bigint,
    live_tuples bigint,
    fragmentation_ratio numeric
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.relname as index_name,
        pg_size_pretty(pg_relation_size(i.oid)) as index_size,
        pg_stat_get_dead_tuples(i.oid) as dead_tuples,
        pg_stat_get_live_tuples(i.oid) as live_tuples,
        CASE 
            WHEN pg_stat_get_dead_tuples(i.oid) > 0 THEN 
                ROUND(pg_stat_get_dead_tuples(i.oid)::numeric / 
                      (pg_stat_get_live_tuples(i.oid) + pg_stat_get_dead_tuples(i.oid)) * 100, 2)
            ELSE 0
        END as fragmentation_ratio
    FROM pg_stat_user_indexes s
    JOIN pg_class i ON s.indexrelid = i.oid
    WHERE s.schemaname = 'public' 
      AND s.relname = 'document_chunk'
      AND i.relname LIKE '%embedding%'
    ORDER BY fragmentation_ratio DESC;
END;
$$ LANGUAGE plpgsql;

-- 添加注释
COMMENT ON VIEW v_vector_index_status IS '向量索引状态监控视图';
COMMENT ON VIEW v_document_statistics IS '文档统计视图';
COMMENT ON FUNCTION search_similar_chunks IS '向量相似度搜索函数';
COMMENT ON FUNCTION search_similar_chunks_in_document IS '文档内向量相似度搜索函数';
COMMENT ON FUNCTION analyze_index_performance IS '索引性能分析函数';
COMMENT ON FUNCTION get_vector_distribution_stats IS '向量分布统计函数';
COMMENT ON FUNCTION check_index_fragmentation IS '索引碎片化检查函数';
