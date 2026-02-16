-- Flyway迁移脚本V6：添加多轮对话支持
-- 创建对话和消息表，支持多轮对话和上下文管理

-- 创建对话表
CREATE TABLE IF NOT EXISTS conversation (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    title VARCHAR(500),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 0
);

-- 创建消息表
CREATE TABLE IF NOT EXISTS message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    context TEXT,
    retrieved_chunks TEXT,
    create_time TIMESTAMP NOT NULL
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_conversation_session_id ON conversation(session_id);
CREATE INDEX IF NOT EXISTS idx_conversation_create_time ON conversation(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_message_conversation_id ON message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_create_time ON message(create_time);

-- 添加外键约束
ALTER TABLE message
ADD CONSTRAINT fk_message_conversation
FOREIGN KEY (conversation_id) REFERENCES conversation(id)
ON DELETE CASCADE;

-- 添加注释
COMMENT ON TABLE conversation IS '对话表，存储用户的对话会话';
COMMENT ON TABLE message IS '消息表，存储对话中的每条消息';
COMMENT ON COLUMN conversation.session_id IS '会话ID，用于标识唯一对话';
COMMENT ON COLUMN conversation.title IS '对话标题';
COMMENT ON COLUMN conversation.message_count IS '消息数量';
COMMENT ON COLUMN message.role IS '角色：user或assistant';
COMMENT ON COLUMN message.context IS 'RAG检索到的上下文';
COMMENT ON COLUMN message.retrieved_chunks IS '检索到的文档片段';
