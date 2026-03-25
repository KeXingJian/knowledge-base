-- Flyway迁移脚本V6：优化全文搜索，添加停用词过滤
-- 解决长查询包含虚词/标点时无法命中的问题

-- 创建停用词过滤函数
CREATE OR REPLACE FUNCTION remove_stopwords(query_text text)
RETURNS text AS $$
DECLARE
    result text := query_text;
BEGIN
    -- 移除标点符号和特殊字符
    result := regexp_replace(result, '[？。，！；：（）【】《》"''"''"…—～｜、]', ' ', 'g');
    result := regexp_replace(result, '\s+', ' ', 'g');

    -- 移除中文停用词（按优先级分组，避免过度误伤）
    -- 第一组：疑问词、语气词（安全移除）
    result := regexp_replace(result, '什么|怎么|为什么|多少|几|谁|哪|哪个|哪些|哪里|哪儿|什么时候|怎样|如何|吗|呢|吧|啊|哦|嗯|呗|罢了|而已|罢|么|嘛', '', 'gi');
    -- 第二组：代词（安全移除）
    result := regexp_replace(result, '我|你|他|她|它|我们|你们|他们|她们|它们|自己|人家', '', 'gi');
    -- 第三组：指示词（安全移除）
    result := regexp_replace(result, '这|那|这些|那些|这个|那个|这里|那里|这边|那边', '', 'gi');
    -- 第四组：常见虚词（可能误伤但概率低）
    result := regexp_replace(result, '的|了|在|是|有|和|就|不|人|都|也|很|到|说|要|去|会|着|没有|看|好|一|一个|上', '', 'gi');
    -- 第五组：介词连词（可能误伤）
    result := regexp_replace(result, '把|被|给|让|叫|使|对于|关于|由于|由|从|自|自从|打|往|向|朝|沿着|顺着|随着|跟|同|与|为了|为着|除了|除开|除去|除', '', 'gi');
    -- 第六组：关联词（可能误伤）
    result := regexp_replace(result, '或者|还是|既|既然|所以|因此|因而|于是|从而|虽然|尽管|即使|哪怕|就算|不论|不管|无论|只要|只有|除非|假如|如果|要是|譬如|例如|比如', '', 'gi');
    result := regexp_replace(result, '便|才|又|再|却|并且|况且|何况|再说|再者|否则|不然|要不|要不然|要么', '', 'gi');

    -- 压缩多余空格并去除首尾空格
    result := regexp_replace(result, '\s+', ' ', 'g');
    result := trim(result);

    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- 重新创建全文搜索函数（带停用词过滤）
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
DECLARE
    cleaned_query text;
BEGIN
    -- 过滤停用词
    cleaned_query := remove_stopwords(search_query);

    -- 如果过滤后为空，只移除标点
    IF cleaned_query IS NULL OR cleaned_query = '' THEN
        cleaned_query := regexp_replace(search_query, '[？。，！；：（）【】《》"''"''"…—～｜、]', ' ', 'g');
        cleaned_query := regexp_replace(cleaned_query, '\s+', ' ', 'g');
        cleaned_query := trim(cleaned_query);
    END IF;

    RETURN QUERY
    SELECT
        dc.id as chunk_id,
        dc.document_id,
        dc.chunk_index,
        dc.content,
        ts_rank(dc.content_tsv, plainto_tsquery('simple', cleaned_query)) as rank,
        dc.create_time
    FROM document_chunk dc
    WHERE dc.content_tsv @@ plainto_tsquery('simple', cleaned_query)
    ORDER BY rank DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- 同时更新混合搜索函数
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
DECLARE
    cleaned_query text;
BEGIN
    -- 过滤停用词
    cleaned_query := remove_stopwords(search_query);

    IF cleaned_query IS NULL OR cleaned_query = '' THEN
        cleaned_query := regexp_replace(search_query, '[？。，！；：（）【】《》"''"''"…—～｜、]', ' ', 'g');
        cleaned_query := regexp_replace(cleaned_query, '\s+', ' ', 'g');
        cleaned_query := trim(cleaned_query);
    END IF;

    RETURN QUERY
    WITH
    text_results AS (
        SELECT
            dc.id,
            ts_rank(dc.content_tsv, plainto_tsquery('simple', cleaned_query)) as text_rank
        FROM document_chunk dc
        WHERE dc.content_tsv @@ plainto_tsquery('simple', cleaned_query)
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
COMMENT ON FUNCTION remove_stopwords IS '过滤中文停用词和标点符号';
COMMENT ON FUNCTION fulltext_search_chunks IS '全文搜索函数（带停用词过滤）';
COMMENT ON FUNCTION hybrid_search_chunks IS '混合搜索函数（带停用词过滤）';
