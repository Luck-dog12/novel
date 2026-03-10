# backend

## 环境变量

- DB_URL：JDBC 连接串（腾讯云 CDB MySQL 示例：`jdbc:mysql://<host>:3306/<db>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false`）
- DB_USER：数据库用户名
- DB_PASSWORD：数据库密码
- DB_POOL_SIZE：连接池大小（默认 10）
- COZE_API_BASE_URL：工作流服务基础地址（默认 `http://127.0.0.1:5000`）
- COZE_API_RUN_PATH：工作流执行路径（默认 `/run`）
- COZE_API_STREAM_PATH：工作流流式执行路径（默认 `/stream_run`）
- COZE_API_TOKEN：工作流服务鉴权 Bearer Token（优先使用）
- COZE_API_KEY：兼容旧变量，`COZE_API_TOKEN` 为空时回退使用
- COZE_API_URL：兼容旧配置；未设置 `COZE_API_BASE_URL` 时可用该变量提供基础地址
- UPLOAD_DIR：上传文件保存目录（容器内默认 `/data/uploads`）

## 容器部署（云服务器 + 腾讯云 CDB）

1. 复制 `.env.example` 为 `.env`，填入 DB 与工作流服务配置
2. 在云服务器执行：
   - `docker compose up -d --build`

上传文件目录使用 Docker volume 持久化：`uploads:/data/uploads`（见 `docker-compose.yml`）
