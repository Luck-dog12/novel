# 写作风格模仿助手 API 接口文档

## 概述

本文档描述了写作风格模仿助手工作流的 HTTP API 接口。该工作流基于参考文档的风格特征，根据用户提供的大纲和内容走向生成高质量文本，支持初始生成和续写两种模式。

### 基础信息

| 项目 | 说明 |
|------|------|
| 基础URL | `http://localhost:5000` |
| 协议 | HTTP/1.1 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| 超时时间 | 900秒（15分钟） |

---

## 接口列表

| 接口 | 方法 | 描述 |
|------|------|------|
| `/run` | POST | 同步执行工作流 |
| `/stream_run` | POST | 流式执行工作流（SSE） |
| `/cancel/{run_id}` | POST | 取消正在执行的任务 |
| `/node_run/{node_id}` | POST | 执行单个节点 |
| `/v1/chat/completions` | POST | OpenAI 兼容接口 |
| `/health` | GET | 健康检查 |
| `/graph_parameter` | GET | 获取工作流参数定义 |

---

## 1. 同步执行工作流

### 请求

```
POST /run
Content-Type: application/json
```

### 请求参数

| 参数名 | 类型 | 必填 | 默认值 | 描述 |
|--------|------|------|--------|------|
| `reference_file` | Object | 条件必填 | null | 参考文档文件对象，初始生成模式必填 |
| `reference_doc` | String | 否 | "" | 参考文档内容，优先级高于 reference_file |
| `genre_type` | String | 否 | "小说" | 题材类型 |
| `writing_direction` | String | 否 | "" | 写作内容走向描述 |
| `is_continue_writing` | Boolean | 否 | false | 是否为续写模式 |

#### reference_file 对象结构

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `url` | String | 是 | 文件URL或本地路径 |
| `file_type` | String | 否 | 文件类型（image/video/audio/document/default） |

#### genre_type 可选值

- `小说`
- `散文`
- `诗歌`
- `剧本`
- `报告`
- `论文`
- `简历`
- `合同`
- `广告文案`
- `产品描述`
- `社交媒体内容`
- `自定义`

### 请求示例

#### 初始生成模式

```json
{
  "reference_file": {
    "url": "https://example.com/reference.md",
    "file_type": "document"
  },
  "genre_type": "小说",
  "writing_direction": "讲述两个少年在迷雾森林中寻找友谊之石的冒险故事",
  "is_continue_writing": false
}
```

或直接提供文档内容：

```json
{
  "reference_doc": "青溪村是一个与世隔绝的神秘村庄，流传着关于友谊之石的传说...",
  "genre_type": "小说",
  "writing_direction": "讲述两个少年在迷雾森林中寻找友谊之石的冒险故事",
  "is_continue_writing": false
}
```

#### 续写模式

```json
{
  "is_continue_writing": true
}
```

### 响应参数

| 参数名 | 类型 | 描述 |
|--------|------|------|
| `final_document` | String | 最终生成的文档内容（Markdown格式） |
| `style_match_report` | String | 风格匹配度报告（Markdown格式） |
| `content_consistency_report` | String | 内容走向一致性报告（Markdown格式） |
| `quality_report` | String | 内容质量评估报告（Markdown格式） |
| `writing_progress` | Object | 写作进度信息 |
| `final_document_file` | String | 最终文档文件路径 |
| `run_id` | String | 本次执行的唯一标识 |

#### writing_progress 对象结构

| 参数名 | 类型 | 描述 |
|--------|------|------|
| `total_words` | Integer | 总字数 |
| `genre_type` | String | 题材类型 |
| `writing_direction` | String | 写作内容走向 |
| `last_updated` | String | 最后更新时间 |
| `current_chapter_index` | Integer | 当前章节索引 |
| `chapters_completed` | Integer | 已完成章节数 |
| `batch_chapters_generated` | Integer | 本次生成的章节数 |
| `total_batches` | Integer | 总批次数 |

### 响应示例

```json
{
  "final_document": "# 最终文档\n\n题材类型：小说\n总字数：6595\n生成时间：未知\n\n---\n## 第一章\n\n...",
  "style_match_report": "# 风格匹配度报告\n\n## 匹配度分数\n85.0/100\n\n...",
  "content_consistency_report": "# 内容走向一致性报告\n\n## 一致性分数\n90.0/100\n\n...",
  "quality_report": "# 内容质量评估报告\n\n## 质量分数\n82.0/100\n\n...",
  "writing_progress": {
    "total_words": 6595,
    "genre_type": "小说",
    "writing_direction": "讲述两个少年在迷雾森林中寻找友谊之石的冒险故事",
    "last_updated": "2026-02-05T12:33:12.196978",
    "current_chapter_index": 4,
    "chapters_completed": 4,
    "batch_chapters_generated": 2,
    "total_batches": 0
  },
  "final_document_file": "/workspace/projects/assets/final_document_final.md",
  "run_id": "f89f329b-3cda-4bb6-8048-bbbd3381e1ab"
}
```

---

## 2. 流式执行工作流（SSE）

### 请求

```
POST /stream_run
Content-Type: application/json
```

### 请求参数

与 `/run` 接口相同。

### 响应格式

Server-Sent Events (SSE) 格式，每个事件的格式如下：

```
event: message
data: {JSON数据}
```

### 响应示例

```
event: message
data: {"type": "node_start", "node_name": "入口节点", "run_id": "xxx"}

event: message
data: {"type": "node_end", "node_name": "入口节点", "output": {...}}

event: message
data: {"type": "message_end", "code": "0", "message": "success", "run_id": "xxx"}
```

---

## 3. 取消执行

### 请求

```
POST /cancel/{run_id}
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `run_id` | String | 是 | 要取消的任务ID |

### 响应参数

| 参数名 | 类型 | 描述 |
|--------|------|------|
| `status` | String | 操作状态 |
| `run_id` | String | 任务ID |
| `message` | String | 操作说明 |

### 响应示例

#### 成功取消

```json
{
  "status": "success",
  "run_id": "f89f329b-3cda-4bb6-8048-bbbd3381e1ab",
  "message": "Cancellation signal sent, task will be cancelled at next await point"
}
```

#### 任务已完成

```json
{
  "status": "already_completed",
  "run_id": "f89f329b-3cda-4bb6-8048-bbbd3381e1ab",
  "message": "Task has already completed"
}
```

#### 任务不存在

```json
{
  "status": "not_found",
  "run_id": "invalid_run_id",
  "message": "No active task found with this run_id"
}
```

---

## 4. 执行单个节点

### 请求

```
POST /node_run/{node_id}
Content-Type: application/json
```

### 路径参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `node_id` | String | 是 | 节点ID |

### 可用节点列表

| 节点ID | 节点名称 | 描述 |
|--------|----------|------|
| `cond_document_upload_node` | 入口节点 | 判断写作模式 |
| `document_upload` | 文档上传与内容提取 | 上传参考文档并提取内容 |
| `genre_select` | 题材类型选择 | 选择题材类型 |
| `outline_define` | 大纲定义 | 生成初始写作大纲 |
| `style_analysis` | 风格分析 | 分析参考文档风格 |
| `outline_refine` | 大纲细化 | 细化写作大纲 |
| `content_generate` | 内容生成 | 生成章节内容 |
| `draft_optimize` | 初稿优化 | 优化内容 |
| `quality_check` | 质量检查 | 检查内容质量 |
| `auto_optimize` | 自动优化 | 根据质量检查结果优化 |
| `init_context` | 初始化上下文 | 创建初始上下文数据 |
| `save_context` | 保存上下文 | 保存上下文到文件 |
| `load_context` | 加载上下文 | 加载上下文文件 |
| `get_next_chapter` | 获取下一章节 | 获取续写章节信息 |
| `continue_write` | 续写内容生成 | 生成续写内容 |
| `update_context` | 更新上下文 | 更新上下文数据 |
| `check_chapter_count` | 检查章节计数 | 检查章节数量 |
| `document_format` | 文档格式化 | 格式化输出文档 |

### 请求示例

```json
{
  "reference_doc": "青溪村是一个与世隔绝的神秘村庄...",
  "genre_type": "小说"
}
```

### 响应示例

```json
{
  "style_features": {
    "vocabulary": ["神秘", "传说", "友谊"],
    "sentence_patterns": ["...是...", "...流传着..."],
    "rhetoric": ["比喻", "拟人"]
  },
  "style_analysis_report": "# 风格分析报告\n\n..."
}
```

---

## 5. OpenAI 兼容接口

### 请求

```
POST /v1/chat/completions
Content-Type: application/json
```

### 请求参数

兼容 OpenAI Chat Completions API 格式：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `model` | String | 否 | 模型名称（兼容参数，实际使用工作流） |
| `messages` | Array | 是 | 消息列表 |
| `stream` | Boolean | 否 | 是否流式返回 |
| `temperature` | Float | 否 | 温度参数（兼容参数） |
| `max_tokens` | Integer | 否 | 最大token数（兼容参数） |

### 请求示例

```json
{
  "model": "writing-assistant",
  "messages": [
    {
      "role": "system",
      "content": "你是一个写作风格模仿助手。"
    },
    {
      "role": "user",
      "content": "请根据以下参考文档风格，写一段关于冒险的故事：青溪村是一个与世隔绝的神秘村庄..."
    }
  ],
  "stream": false
}
```

---

## 6. 健康检查

### 请求

```
GET /health
```

### 响应示例

```json
{
  "status": "ok",
  "message": "Service is running"
}
```

---

## 7. 获取工作流参数定义

### 请求

```
GET /graph_parameter
```

### 响应示例

```json
{
  "input_schema": {
    "properties": {
      "reference_file": {
        "anyOf": [
          {"$ref": "#/definitions/File"},
          {"type": "null"}
        ],
        "default": null,
        "description": "参考文档文件（.md或.txt格式），初始生成模式必填，续写模式可选"
      },
      "reference_doc": {
        "default": "",
        "description": "参考文档内容（续写模式使用），优先级高于reference_file",
        "type": "string"
      },
      "genre_type": {
        "default": "小说",
        "description": "题材类型",
        "type": "string"
      },
      "writing_direction": {
        "default": "",
        "description": "写作内容走向描述",
        "type": "string"
      },
      "is_continue_writing": {
        "default": false,
        "description": "是否为续写模式",
        "type": "boolean"
      }
    },
    "title": "GraphInput",
    "type": "object"
  },
  "output_schema": {
    "properties": {
      "final_document": {
        "description": "最终生成的文档内容",
        "type": "string"
      },
      "style_match_report": {
        "description": "风格匹配度报告",
        "type": "string"
      },
      "content_consistency_report": {
        "description": "内容走向一致性报告",
        "type": "string"
      },
      "quality_report": {
        "description": "内容质量评估报告",
        "type": "string"
      },
      "writing_progress": {
        "description": "写作进度信息",
        "type": "object"
      },
      "final_document_file": {
        "default": "",
        "description": "最终文档文件路径",
        "type": "string"
      }
    },
    "required": ["final_document", "style_match_report", "content_consistency_report", "quality_report", "writing_progress"],
    "title": "GraphOutput",
    "type": "object"
  }
}
```

---

## 错误响应

### 错误响应格式

```json
{
  "detail": {
    "error_code": "ERROR_CODE",
    "error_message": "错误描述",
    "stack_trace": "错误堆栈"
  }
}
```

### 常见错误码

| HTTP状态码 | 错误码 | 描述 |
|------------|--------|------|
| 400 | INVALID_JSON | JSON格式错误 |
| 404 | NODE_NOT_FOUND | 节点不存在 |
| 500 | INTERNAL_ERROR | 内部错误 |
| 503 | SERVICE_UNAVAILABLE | 服务不可用 |

---

## 使用流程

### 初始生成模式

1. 调用 `/run` 接口，提供参考文档和写作方向
2. 系统自动生成前1-2章内容
3. 上下文自动保存到 `assets/writing_context.json`
4. 返回最终文档和质量报告

### 续写模式

1. 调用 `/run` 接口，设置 `is_continue_writing: true`
2. 系统自动加载上下文文件
3. 每次续写生成2个章节
4. 自动进行质量检查和优化
5. 返回包含所有已写内容的最终文档

---

## 约束条件

| 约束项 | 限制值 |
|--------|--------|
| 每次写作字数上限 | 10000字 |
| 质量达标阈值 | 75分 |
| 参考文档格式 | .md 或 .txt |
| 续写模式每次章节数 | 2个章节 |
| 续写模式质量检查次数 | 最多2次 |
| 每章字数范围 | 2200-2700字 |
| 请求超时时间 | 900秒（15分钟） |

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.0.0 | 2026-02-05 | 初始版本 |
