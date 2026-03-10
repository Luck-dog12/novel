# backend

## 环境变量

- DB_URL：JDBC 连接串（腾讯云 CDB MySQL 示例：`jdbc:mysql://<host>:3306/<db>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false`）
- DB_USER：数据库用户名
- DB_PASSWORD：数据库密码
- DB_POOL_SIZE：连接池大小（默认 10）
- COZE_API_KEY：Coze API Key
- UPLOAD_DIR：上传文件保存目录（容器内默认 `/data/uploads`）

## 容器部署（云服务器 + 腾讯云 CDB）

1. 复制 `.env.example` 为 `.env`，填入 DB 与 COZE 配置
2. 在云服务器执行：
   - `docker compose up -d --build`

上传文件目录使用 Docker volume 持久化：`uploads:/data/uploads`（见 `docker-compose.yml`）
