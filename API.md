# 知识库系统 API 文档

## 通用响应格式

所有接口返回统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码，200成功，500失败 |
| message | String | 响应消息 |
| data | T | 响应数据，具体类型见各接口说明 |

---

## 一、问答接口

### 1.1 提问接口

**功能**：向知识库提问，获取基于RAG的智能回答

**接口地址**：`POST /api/qa/ask`

**入参**：
```json
{
  "question": "如何使用Spring Boot？"
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| question | String | 是 | 问题内容 |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": "Spring Boot是一个快速开发框架..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| data | String | AI生成的回答 |

---

## 二、对话管理接口

### 2.1 聊天接口

**功能**：基于会话的连续对话，支持上下文记忆

**接口地址**：`POST /api/conversations/chat`

**入参**：
```json
{
  "sessionId": "session-123",
  "question": "刚才说的配置文件在哪里？"
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | 是 | 会话ID，用于关联对话历史 |
| question | String | 是 | 问题内容 |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "配置文件位于 src/main/resources/application.yaml",
    "sessionId": "session-123"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| answer | String | AI回答 |
| sessionId | String | 会话ID |

---

### 2.2 获取对话详情

**功能**：根据会话ID获取对话信息

**接口地址**：`GET /api/conversations/{sessionId}`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | 是 | 会话ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "sessionId": "session-123",
    "title": "Spring Boot配置问题",
    "createTime": "2026-02-16T10:00:00",
    "updateTime": "2026-02-16T10:30:00",
    "messageCount": 5,
    "messages": []
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 对话ID |
| sessionId | String | 会话ID |
| title | String | 对话标题 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |
| messageCount | Integer | 消息数量 |
| messages | List<Message> | 消息列表 |

---

### 2.3 获取所有对话列表

**功能**：获取系统中所有对话

**接口地址**：`GET /api/conversations`

**入参**：无

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "sessionId": "session-123",
      "title": "Spring Boot配置问题",
      "createTime": "2026-02-16T10:00:00",
      "updateTime": "2026-02-16T10:30:00",
      "messageCount": 5
    }
  ]
}
```

---

### 2.4 获取对话消息

**功能**：获取指定会话的所有消息记录

**接口地址**：`GET /api/conversations/{sessionId}/messages`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | 是 | 会话ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "role": "USER",
      "content": "如何配置数据库？",
      "createTime": "2026-02-16T10:00:00"
    },
    {
      "id": 2,
      "role": "ASSISTANT",
      "content": "在application.yaml中配置...",
      "createTime": "2026-02-16T10:00:05"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 消息ID |
| role | String | 角色：USER/ASSISTANT |
| content | String | 消息内容 |
| createTime | LocalDateTime | 创建时间 |

---

### 2.5 删除对话

**功能**：删除指定会话及其所有消息

**接口地址**：`DELETE /api/conversations/{sessionId}`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | String | 是 | 会话ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": "删除对话成功"
}
```

---

## 三、文档管理接口

### 3.1 批量上传文档

**功能**：批量上传文档到知识库，支持PDF、TXT等格式

**接口地址**：`POST /api/documents/batch-upload`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| files | MultipartFile[] | 是 | 文件数组，最多100个 |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": "batch-task-123456"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| data | String | 批量上传任务ID |

---

### 3.2 获取批量上传进度

**功能**：查询批量上传任务的进度

**接口地址**：`GET /api/documents/batch-upload/progress/{taskId}`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | String | 是 | 任务ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "batch-task-123456",
    "totalFiles": 10,
    "processedFiles": 7,
    "failedFiles": 1,
    "status": "PROCESSING",
    "progress": 70,
    "startTime": "2026-02-16T10:00:00",
    "estimatedEndTime": "2026-02-16T10:05:00",
    "errors": [
      {
        "fileName": "error.pdf",
        "error": "文件格式不支持"
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | String | 任务ID |
| totalFiles | Integer | 总文件数 |
| processedFiles | Integer | 已处理文件数 |
| failedFiles | Integer | 失败文件数 |
| status | String | 状态：PENDING/PROCESSING/COMPLETED/FAILED |
| progress | Integer | 进度百分比 |
| startTime | LocalDateTime | 开始时间 |
| estimatedEndTime | LocalDateTime | 预计结束时间 |
| errors | List | 错误信息列表 |

---

### 3.3 获取文档列表

**功能**：获取知识库中所有文档

**接口地址**：`GET /api/documents/list`

**入参**：无

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "fileName": "SpringBoot指南.pdf",
      "fileType": "pdf",
      "filePath": "documents/uuid/file.pdf",
      "fileSize": 1024000,
      "fileHash": "abc123...",
      "chunkCount": 50,
      "uploadTime": "2026-02-16T10:00:00",
      "updateTime": "2026-02-16T10:00:00",
      "processed": true,
      "errorMessage": null
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文档ID |
| fileName | String | 文件名 |
| fileType | String | 文件类型 |
| filePath | String | 文件路径 |
| fileSize | Long | 文件大小（字节） |
| fileHash | String | 文件哈希值 |
| chunkCount | Integer | 分片数量 |
| uploadTime | LocalDateTime | 上传时间 |
| updateTime | LocalDateTime | 更新时间 |
| processed | Boolean | 是否已处理 |
| errorMessage | String | 错误信息 |

---

### 3.4 获取文档详情

**功能**：根据ID获取文档的MinIO预签名URL

**接口地址**：`GET /api/documents/{id}`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文档ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "success",
  "data": "http://minio-server:9000/documents/abc123/file.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| data | String | MinIO预签名URL，有效期1小时 |

**说明**：
- 返回的URL为MinIO预签名URL，可直接用于下载文件
- URL有效期默认为3600秒（1小时）
- 过期后需要重新调用接口获取新的URL

---

### 3.5 删除文档

**功能**：删除指定文档及其所有分片

**接口地址**：`DELETE /api/documents/{id}`

**入参**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 文档ID（路径参数） |

**返回值**：
```json
{
  "code": 200,
  "message": "文档删除成功",
  "data": null
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 500 | 服务器内部错误 |
