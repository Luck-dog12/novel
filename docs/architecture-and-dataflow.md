# 项目架构与数据流向（novel）

本文用“数据结构 + 流向”方式把当前仓库的 Android App 与 Kotlin 后端串起来，便于定位：数据从哪来、到哪去、落在哪里、失败点在哪。

## 1. 总览

**运行时组件**
- Android App（Compose UI + Ktor Client + OkHttp）
- 后端服务（Kotlin + Ktor(Netty) + HikariCP + MySQL/H2）
- 数据库（MySQL；可选 fallback：H2 in-memory）
- 外部模型服务（Coze Chat Completions API）
- 文件存储（后端本地磁盘 uploads 目录；Compose 下映射为 volume）

**端到端主链路**
- App UI 触发 → App 网络层请求 → 后端路由 → 后端 Service 读写 DB/文件/Coze → 返回 JSON → App UI 展示/保存本地会话信息

### 1.1 ASCII 数据流图（总览）

```text
                                     +-------------------+
                                     |   Coze API        |
                                     | chat/completions  |
                                     +---------^---------+
                                               |
                                               | (HTTP, Bearer COZE_API_KEY)
                                               |
+----------------------+     HTTP(S)     +------|-------------------+
| Android App          |  -------------> | Backend (Ktor/Netty)     |
| (Compose UI)         |                | Routes: /api/v1/*         |
|                      | <-------------  | Services                 |
|  +----------------+  |   JSON          | - GenerationService      |
|  | Screens        |  |                | - ProjectService         |
|  | - Initial      |  |                | - ContextService         |
|  | - Continue     |  |                | - HistoryService         |
|  | - Config       |  |                | - DocumentService        |
|  | - History      |  |                +----+-----------+----------+
|  +-------+--------+  |                     |           |
|          |           |                     |           |
|          v           |                     |           |
|  +----------------+  |                     |           |
|  | ApiService     |  |                     |           |
|  | (DTO + URL)    |  |                     |           |
|  +-------+--------+  |                     |           |
|          |           |                     |           |
|          v           |                     |           |
|  +----------------+  |                     |           |
|  | ApiClient       | |                     |           |
|  | (Ktor+OkHttp)   | |                     |           |
|  | baseUrl=BuildConfig.API_BASE_URL        |           |
|  +-------+--------+  |                     |           |
|          |           |                     |           |
|          |  sessionId|                     |           |
|          v  (save)   |                     |           |
|  +----------------+  |                     |           |
|  | SessionIdStore |  |                     |           |
|  | (SharedPrefs)  |  |                     |           |
|  +----------------+  |                     |           |
+----------------------+                     |           |
                                             |           |
                                             v           v
                                   +----------------+  +----------------------+
                                   | MySQL / H2     |  | uploads dir (volume) |
                                   | HikariCP pool  |  | documents.file_path  |
                                   +----------------+  +----------------------+
```

### 1.2 ASCII 数据流图（初始生成 / 续写）

```text
初始生成（POST /api/v1/generation）

Android UI
  |
  v
ApiService.generateContent()
  |
  v
POST {API_BASE_URL}/v1/generation  ------------------------------+
  |                                                             |
  v                                                             |
Routes.kt: POST /api/v1/generation                               |
  |                                                             |
  v                                                             |
GenerationService.generateContent()                              |
  |                                                             |
  +--> call Coze API (prompt/params) ------------------------+   |
  |                                                         |   |
  +--> INSERT generation_history                             |   |
  +--> UPSERT/INSERT context_entries (sessionId 相关上下文)   |   |
  |                                                         |   |
  +--> return { content, sessionId, ... } <------------------+---+
  |
  v
Android: SessionIdStore.put(projectId, sessionId) -> ResultScreen 展示


续写（依赖 sessionId + context）

Android ContinueWritingScreen
  |
  +--> SessionIdStore.get(projectId)
  |      |
  |      +--(缺失)--> GET /api/v1/sessions/latest?projectId=... -> sessionId
  |
  +--> GET /api/v1/context?projectId=...&sessionId=...
  |
  +--> POST /api/v1/generation (isContinueWriting=true, sessionId=...)
           |
           +--(context 不存在)--> 409 NoContext
           +--(context 存在)-----> 生成并写入 history/context -> 返回内容
```

### 1.3 ASCII 数据流图（文档上传）

```text
Android FilePicker (Uri)
  |
  v
read bytes + metadata(fileName,fileType)
  |
  v
POST /api/v1/documents (multipart: projectId + file) ----------------------+
  |                                                                        |
  v                                                                        |
Routes.kt: POST /api/v1/documents                                          |
  |                                                                        |
  v                                                                        |
DocumentService.upload()                                                   |
  |                                                                        |
  +--> write file to ${UPLOAD_DIR}/<uuid>.<ext>                             |
  +--> INSERT documents (file_path, content_hash, size, ...)                |
  |                                                                        |
  +--> return Document metadata <-------------------------------------------+
```

## 2. 后端（backend）结构

### 2.1 入口与装配
- 后端入口： [Application.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/Application.kt#L1-L19)
  - 启动 Netty：`host=0.0.0.0`、`port=8080`
  - 启动即初始化数据库：`DatabaseService.init()`
  - 安装 JSON 序列化（ContentNegotiation）
  - 注册路由：`configureRouting()`

### 2.2 路由与 API 形状
- 路由集中在： [Routes.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/api/Routes.kt#L1-L173)
- 健康检查：
  - `GET /health` → `OK`
- API 前缀：`/api/v1`

**项目（Projects）**
- `GET /api/v1/projects`：列表
- `POST /api/v1/projects`：创建
- `GET /api/v1/projects/{id}`：详情
- `PUT /api/v1/projects/{id}`：更新
- `DELETE /api/v1/projects/{id}`：删除

**配置（Config）**
- `GET /api/v1/config/{projectId}`：取配置
- `POST /api/v1/config`：保存配置（按 projectId upsert）

**生成（Generation）**
- `POST /api/v1/generation`：初始生成/续写

**历史（History）**
- `GET /api/v1/history?projectId=...`：按项目筛选；不传返回全量

**上下文（Context）与会话（Sessions）**
- `POST /api/v1/context`：保存上下文条目
- `GET /api/v1/context?projectId=...&sessionId=...`：查上下文（sessionId 可选）
- `GET /api/v1/sessions/latest?projectId=...`：获取最近一次 sessionId

**文档（Documents）**
- `POST /api/v1/documents`：multipart 上传（字段：`projectId` + 文件）
- `GET /api/v1/documents/{id}`：文档元信息
- `DELETE /api/v1/documents/{id}`：删除元信息 + 删除磁盘文件

### 2.3 数据库连接与迁移（表结构来源）

**连接方式**
- 连接入口： [DatabaseService.init](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/DatabaseService.kt#L13-L43)
- 环境变量读取：`DB_URL / DB_USER / DB_PASSWORD / DB_POOL_SIZE / REQUIRE_DB`
- 连接池：HikariCP（`HikariDataSource`）

**MySQL vs H2 fallback 逻辑**
- `DB_URL` 为空：
  - `REQUIRE_DB=true` → 直接报错（不允许启动）
  - `REQUIRE_DB!=true` → 回退到 `jdbc:h2:mem:novel;MODE=MySQL...`
  - 逻辑见：[DatabaseService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/DatabaseService.kt#L18-L26)

**表结构（启动时自动创建）**
- 迁移入口：`DatabaseService.migrate()`：[DatabaseService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/DatabaseService.kt#L45-L120)

1) `writing_projects`
- `id VARCHAR(36) PK`
- `title VARCHAR(255)`
- `description TEXT`
- `genre_type VARCHAR(100)`
- `creation_date VARCHAR(64)`
- `last_modified_date VARCHAR(64)`
- `status VARCHAR(50)`
- `word_count INT`

2) `project_configs`
- `id VARCHAR(36) PK`
- `project_id VARCHAR(36) UNIQUE`
- `genre_type VARCHAR(100)`
- `writing_direction TEXT`
- `max_length INT`
- `creativity_level VARCHAR(20)`
- `timestamp VARCHAR(64)`

3) `generation_history`
- `id VARCHAR(36) PK`
- `project_id VARCHAR(36)`
- `generation_type VARCHAR(50)`
- `input_params TEXT`
- `output_content TEXT`
- `generation_date VARCHAR(64)`
- `duration BIGINT`

4) `documents`
- `id VARCHAR(36) PK`
- `project_id VARCHAR(36)`
- `file_name VARCHAR(255)`
- `file_type VARCHAR(20)`
- `file_size BIGINT`
- `upload_date VARCHAR(64)`
- `file_path TEXT`
- `content_hash VARCHAR(64)`

5) `context_entries`
- `id VARCHAR(36) PK`
- `project_id VARCHAR(36)`
- `session_id VARCHAR(36)`
- `context_type VARCHAR(100)`
- `context_content TEXT`
- `last_updated VARCHAR(64)`
- 索引：
  - `(project_id, session_id, context_type, last_updated)`
  - `(project_id, last_updated)`

### 2.4 后端 Service 责任边界（从“写什么表/依赖什么外部”角度）

- 项目 CRUD： [ProjectService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/ProjectService.kt)
  - 写/读：`writing_projects`
  - 额外：进程内缓存（5 分钟），键类似 `projects:all`
- 配置保存： [ConfigService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/ConfigService.kt)
  - 写/读：`project_configs`（upsert）
- 生成与上下文： [GenerationService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/GenerationService.kt#L33-L292)
  - 外部依赖：Coze（HTTP 调用 `https://api.coze.cn/v1/chat/completions`）
  - 写入：
    - `generation_history`（每次生成一条）
    - `context_entries`（生成后沉淀上下文）
  - 续写缺上下文：会抛 `NoContextException`，路由层返回 409
- 历史查询： [HistoryService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/HistoryService.kt)
  - 读写：`generation_history`
- 上下文存取与 latest session： [ContextService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/ContextService.kt)
  - 读写：`context_entries`
- 文档上传/删除： [DocumentService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/DocumentService.kt)
  - 写/读：`documents`
  - 文件落盘：`${UPLOAD_DIR}/{uuid}.{ext}`（UPLOAD_DIR 默认 `uploads`）

### 2.5 后端运行配置（你日常最常改的变量）
- Docker Compose 环境变量注入： [docker-compose.yml](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/docker-compose.yml#L1-L19)
- `.env` 模板： [backend/.env.example](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/.env.example#L1-L8)

关键变量含义：
- `DB_URL/DB_USER/DB_PASSWORD`：数据库连接
- `REQUIRE_DB`：是否允许回退 H2
- `COZE_API_KEY`：Coze 鉴权
- `APP_SECRET_KEY`：后端安全相关（token/加密）派生密钥
- `UPLOAD_DIR`：文档落盘目录（compose 下映射到 volume）

## 3. Android（frontend/android）结构

### 3.1 入口与页面组织方式
- 入口 Activity： [MainActivity.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/MainActivity.kt#L27-L105)
  - 当前用 `UiSandbox()` 以字符串状态切换各 Screen
  - 未引入 Navigation 组件（所以页面跳转逻辑分散在 UI 内）

### 3.2 网络层与后端基址（Base URL）

**Base URL 从哪里来**
- Gradle 在不同 buildType 下写入 `BuildConfig.API_BASE_URL`：
  - [app/build.gradle](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/build.gradle#L8-L37)
  - debug 默认：`http://10.0.2.2:8080/api`
  - release 默认：`https://api.novel-writing-assistant.com/api`
- Ktor Client 实际使用值：
  - [ApiClient.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/network/ApiClient.kt#L11-L13)

**请求形状**
- `ApiService` 负责拼 URL 与序列化 DTO：
  - [ApiService.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/network/ApiService.kt)
- 约定：Android 端用 `{baseUrl}/v1/...`，后端对应 `/api/v1/...`

### 3.3 本地状态与存储（重点：sessionId）
- `SessionIdStore`：SharedPreferences 按 projectId 保存 sessionId
  - [SessionIdStore.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/utils/SessionIdStore.kt#L5-L25)
- `CacheManager`：进程内 TTL 缓存（当前用于 projects 列表）
  - [CacheManager.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/utils/CacheManager.kt)

### 3.4 Room（本地数据库）
- Room 数据库入口： [AppDatabase.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/AppDatabase.kt#L8-L41)
- Entity/Dao：
  - [WritingProject.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/WritingProject.kt)
  - [UserConfig.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/UserConfig.kt)
  - [GenerationHistory.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/GenerationHistory.kt)
  - [ReferenceDocument.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/ReferenceDocument.kt)
  - [ContextInfo.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/data/ContextInfo.kt)

现状要点：
- Room 结构已存在，但 UI 当前主要走“直接网络请求”，尚未形成“后端数据 → 落库 → UI 订阅 DB”的完整链路。

### 3.5 文件选择与本地文件
- 文件选择与复制： [FilePicker.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/utils/FilePicker.kt#L66-L82)
  - 选择的 Uri 会被拷贝到 `getExternalFilesDir(DIRECTORY_DOCUMENTS)`（App 私有外部目录）
- 上传（网络层已实现 multipart）：
  - [ApiService.uploadDocument](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/network/ApiService.kt#L68-L90)

## 4. 关键数据结构（按“业务对象”对齐前后端）

### 4.1 Writing Project（写作项目）
- 后端存储：`writing_projects`
- 后端 API：`/api/v1/projects`
- Android 本地：`WritingProject`（Room entity）
  - 注意：Android 端当前未把 projects 完整接入 UI 主流程，但 API 已具备。

### 4.2 Project Config（项目配置）
- 后端存储：`project_configs`
- 后端 API：`/api/v1/config`
- Android：
  - 屏幕： [ConfigScreen.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/ui/screens/ConfigScreen.kt)
  - 也有本地 `UserConfig`（Room），但概念上更偏“用户侧配置”，与后端配置模型不完全等价。

### 4.3 Generation（生成/续写）
- 后端 API：`POST /api/v1/generation`
- 后端写入：
  - `generation_history`（记录输入参数、输出内容、耗时）
  - `context_entries`（沉淀上下文）
- Android：
  - 初始生成页： [InitialGenerationScreen.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/ui/screens/InitialGenerationScreen.kt)
  - 续写页： [ContinueWritingScreen.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/ui/screens/ContinueWritingScreen.kt)
  - 本地 sessionId：SharedPreferences（见 3.3）

### 4.4 Context（上下文）与 Session（会话）
- 后端存储：`context_entries`
- 后端 API：
  - `GET /api/v1/context`
  - `POST /api/v1/context`
  - `GET /api/v1/sessions/latest`
- Android：
  - sessionId 优先来自本地 `SessionIdStore`
  - 若本地缺失，则调用 latest session 恢复

### 4.5 Documents（参考文档）
- 后端存储：`documents` + 文件落盘
- 后端 API：`POST/GET/DELETE /api/v1/documents`
- Android：
  - FilePicker 获取 bytes
  - ApiService 支持 multipart 上传

## 5. 端到端数据流（按“用户操作”拆解）

### 5.1 初始生成（最常用主路径）
1) Android：用户在 [InitialGenerationScreen.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/ui/screens/InitialGenerationScreen.kt) 填入 prompt/参数
2) Android → 后端：`POST {API_BASE_URL}/v1/generation`
3) 后端路由：`POST /api/v1/generation` → 调用 [GenerationService.generateContent](file:///d:/%E9%A1%B9%E7%9B%AE/novel/backend/src/main/kotlin/com/novel/writing/assistant/service/GenerationService.kt#L33-L292)
4) 后端 → 外部：请求 Coze（Bearer `COZE_API_KEY`）
5) 后端写 DB：
  - `generation_history` 插入一条记录
  - `context_entries` 插入/更新上下文条目
6) 后端返回 JSON（包含 `sessionId`）
7) Android：把 `sessionId` 按 `projectId` 保存到 [SessionIdStore.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/utils/SessionIdStore.kt)
8) Android：跳转结果页展示内容

### 5.2 续写
1) Android：进入 [ContinueWritingScreen.kt](file:///d:/%E9%A1%B9%E7%9B%AE/novel/frontend/android/app/src/main/java/com/novel/writing/assistant/ui/screens/ContinueWritingScreen.kt)
2) sessionId 获取顺序：
  - 本地 `SessionIdStore` 有 → 直接用
  - 没有 → `GET /api/v1/sessions/latest?projectId=...` 拉取 latest sessionId
3) Android：`GET /api/v1/context?projectId=...&sessionId=...` 获取上下文
4) Android：点击续写 → `POST /api/v1/generation`（带 `isContinueWriting=true`、`sessionId`）
5) 后端：若上下文缺失 → `NoContextException` → 路由返回 409（Android 需要提示用户先进行初次生成或恢复上下文）

### 5.3 配置保存
1) Android：`GET /api/v1/config/{projectId}` 回填 UI
2) Android：用户保存 → `POST /api/v1/config`
3) 后端：`project_configs` 按 projectId upsert

### 5.4 文档上传
1) Android：FilePicker 读文件并得到 bytes
2) Android：`POST /api/v1/documents` multipart
3) 后端：
  - 文件写入 `${UPLOAD_DIR}/...`
  - `documents` 写元信息（含 hash、path）

## 6. 常见“乱”的来源（快速定位方式）

**1) Base URL 不一致**
- Android 实际请求基址是 `BuildConfig.API_BASE_URL`（来自 build.gradle 的 buildConfigField）
- 后端实际监听是 8080，API 前缀是 `/api/v1`
- 因此 Android 侧 `{API_BASE_URL}` 需要是 `http(s)://host:8080/api`（再拼 `/v1/...`）

**2) 续写失败（409 NoContext）**
- 续写依赖 `sessionId` + `context_entries` 中的上下文条目
- 解决思路：先确保初次生成成功（写入 context），再续写；或用 `sessions/latest` 恢复 sessionId

**3) 数据“既在后端 DB 又在手机本地 DB”，概念重叠**
- 后端 DB 是权威数据源（项目、配置、历史、上下文、文档元信息）
- Android Room 当前更多是“预留/将来可做离线缓存”，但 UI 还未以 Room 为驱动

## 7. 你可以怎么用这份文档排障

- 服务是否启动：`GET /health`
- 是否连上 MySQL：后端日志出现 `HikariPool-1 - Start completed` 且 `jdbcUrl=jdbc:mysql://...`
- 续写链路是否完整：本地是否保存 `sessionId`，以及后端是否存在 `context_entries`
- 文档是否落盘：后端 `documents.file_path` + 上传目录是否映射到 volume

