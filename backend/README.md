# backend

## 环境变量

- DB_URL：JDBC 连接串（腾讯云 CDB MySQL 示例：`jdbc:mysql://<host>:3306/<db>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false`）
- DB_USER：数据库用户名
- DB_PASSWORD：数据库密码
- DB_POOL_SIZE：连接池大小（默认 10）
- COZE_BRIDGE_BASE_URL：后端调用 Coze SDK 桥接服务的基础地址（默认 `http://127.0.0.1:8787`）
- COZE_BRIDGE_RUN_PATH：桥接服务同步执行路径（默认 `/run`）
- COZE_BRIDGE_STREAM_PATH：桥接服务流式执行路径（默认 `/stream_run`）
- COZE_BRIDGE_TOKEN：后端到桥接服务的可选 Bearer Token
- COZE_API_BASE：Coze OpenAPI 地址（中国区建议 `https://api.coze.cn`）
- COZE_OAUTH_CLIENT_ID：OAuth 应用 Client ID（通常形如 `xxx.app.coze`）
- COZE_OAUTH_CLIENT_SECRET：OAuth 应用 Client Secret（授权码模式必填）
- COZE_OAUTH_REDIRECT_URL：OAuth 回调地址（需与应用配置一致，本机联调可用 `http://127.0.0.1:8080/api/v1/oauth/coze/callback`）
- COZE_ENV_FILE：可选，Bridge 回调后写入 `access_token/refresh_token` 的 `.env` 路径（默认 backend/.env）
- COZE_OAUTH_CODE：OAuth 授权码（首次换取 access_token 使用）
- COZE_OAUTH_EXPECTED_STATE：可选，回调 state 校验值
- COZE_OAUTH_ACCESS_TOKEN：可选，已获取的 access_token
- COZE_OAUTH_REFRESH_TOKEN：可选，refresh_token，用于自动续期
- COZE_OAUTH_EXPIRES_AT：可选，access_token 过期时间（Unix 秒）
- COZE_WORKFLOW_ID：要执行的工作流 ID
- COZE_BOT_ID：可选，关联 Bot ID
- COZE_APP_ID：可选，关联 App ID
- COZE_BRIDGE_USER_ID：桥接服务默认 user_id
- UPLOAD_DIR：上传文件保存目录（容器内默认 `/data/uploads`）

## 容器部署（云服务器 + 腾讯云 CDB）

1. 复制 `.env.example` 为 `.env`，填入 DB 与 Coze SDK 桥接配置
2. 在云服务器执行：
   - `docker compose up -d --build`

上传文件目录使用 Docker volume 持久化：`uploads:/data/uploads`（见 `docker-compose.yml`）

OAuth 回调入口：
- `GET /api/v1/oauth/coze/callback?code=...&state=...`
- 后端会把授权码转发到 `coze-sdk-bridge` 完成换 token，并缓存到桥接服务内存中。
- 成功回调后会自动把 `COZE_OAUTH_ACCESS_TOKEN / COZE_OAUTH_REFRESH_TOKEN / COZE_OAUTH_EXPIRES_AT` 写回 `.env`（本机文件存在时）。

OAuth 授权链接生成入口（Bridge）：
- `GET /oauth/authorize-url`
- 返回 `authorize_url`，该地址使用 `www.coze.cn` 授权域名并自动携带 `client_id/redirect_uri/state`。
