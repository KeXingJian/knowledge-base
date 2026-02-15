-- Flyway迁移脚本V2：创建向量索引
-- 为document_chunk表的embedding字段创建向量索引，加速向量相似度检索

-- 创建IVFFlat索引（适合中等规模数据集，约10万-100万条记录）
-- IVFFlat索引在查询时需要指定ivfflat.probes参数，建议设置为sqrt(列表数量)
CREATE INDEX IF NOT EXISTS idx_embedding_ivfflat ON document_chunk 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- 创建HNSW索引（适合大规模数据集，查询速度更快，但索引更大）
-- HNSW索引不需要额外参数，查询速度更快
CREATE INDEX IF NOT EXISTS idx_embedding_hnsw ON document_chunk 
USING hnsw (embedding vector_cosine_ops);

-- 为embedding字段创建余弦相似度索引（如果只需要余弦相似度）
CREATE INDEX IF NOT EXISTS idx_embedding_cosine ON document_chunk 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- 为embedding字段创建欧氏距离索引（如果需要欧氏距离）
CREATE INDEX IF NOT EXISTS idx_embedding_l2 ON document_chunk 
USING ivfflat (embedding vector_l2_ops) 
WITH (lists = 100);

-- 为embedding字段创建内积索引（如果需要内积）
CREATE INDEX IF NOT EXISTS idx_embedding_ip ON document_chunk 
USING ivfflat (embedding vector_ip_ops) 
WITH (lists = 100);

-- 设置IVFFlat索引的probes参数（影响查询精度和速度）
-- 建议设置为sqrt(列表数量)或更高，提高查询精度
SET ivfflat.probes = 10;

-- 设置HNSW索引的ef_search参数（影响查询精度和速度）
-- 建议设置为40-100，提高查询精度
SET hnsw.ef_search = 40;
