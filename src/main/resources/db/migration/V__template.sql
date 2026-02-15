-- Flyway 迁移脚本模板
-- 复制此文件创建新的迁移脚本

-- 命名规范：V{版本号}__{描述}.sql
-- 例如：V4__add_user_table.sql

-- 在此编写 SQL 语句
-- 建议：
-- 1. 使用 IF NOT EXISTS 确保幂等性
-- 2. 添加注释说明变更内容
-- 3. 使用事务确保数据一致性

-- 示例：
-- BEGIN;

-- CREATE TABLE IF NOT EXISTS example_table (
--     id BIGSERIAL PRIMARY KEY,
--     name VARCHAR(255) NOT NULL,
--     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );

-- CREATE INDEX IF NOT EXISTS idx_example_name ON example_table(name);

-- COMMIT;

-- 添加注释
-- COMMENT ON TABLE example_table IS '示例表';
-- COMMENT ON COLUMN example_table.name IS '名称';
