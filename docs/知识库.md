# 部署与运维命令详解 (Novel Backend)

本文档提供本项目（novel）常用的运维命令、详细解释以及在当前项目中的具体使用示例。

---

## 1. 文件传输 (Windows → Linux)

使用 `scp` (Secure Copy) 在本地 Windows 开发机与 ECS 服务器之间安全传输文件。

### 上传文件
**命令含义**：把本地文件复制到远程服务器指定目录。
- **参数**：
  - `源文件`：本地路径（支持绝对/相对路径）。
  - `目标`：`用户@IP:路径`（如 `root@1.2.3.4:/root/`）。

**本项目示例**（上传本地构建好的后端镜像包）：
```powershell
# 将本地构建好的 Docker 镜像包传到服务器 root 目录，用于后续 docker load
scp D:\项目\novel\backend\novel-backend.tar root@<ECS公网IP>:/root/
```

### 上传目录
**命令含义**：递归复制整个文件夹内容。
- **参数**：
  - `-r`：递归复制（Recursive）。

**本项目示例**（首次部署或全量更新后端代码）：
```powershell
# 将整个 backend 目录代码传到服务器 /opt/novel/ 下
scp -r D:\项目\novel\backend root@<ECS公网IP>:/opt/novel/
```

### 下载文件
**命令含义**：从远程服务器拉取文件到本地。

**本项目示例**（备份服务器上的环境变量文件）：
```powershell
# 把服务器上的 .env 配置下载到本地 D:\backup 备份，防止误删
scp root@<ECS公网IP>:/opt/novel/backend/.env D:\backup\env.bak
```

---

## 2. 后端发布流程 (完整闭环)

由于服务器（深圳 ECS）无法直接访问 Docker Hub，我们采用 **“本地构建镜像 → 导出 tar → 服务器导入”** 的离线更新策略。

### Step 1: 本地构建与导出 (Windows PowerShell)
在开发机上执行，生成可传输的镜像包。

```powershell
# 1. 进入后端目录
cd D:\项目\novel\backend

# 2. 构建镜像
# -t: 给镜像打标签 (tag)，这里定死为 novel-backend:latest 方便 compose 使用
# .: 使用当前目录的 Dockerfile
docker build -t novel-backend:latest .

# 3. 导出镜像为文件
# -o: 输出文件名
# 导出对象: novel-backend:latest 这个镜像
docker save -o novel-backend.tar novel-backend:latest

# 4. 上传到服务器 (假设服务器 IP 为 120.25.x.x)
scp .\novel-backend.tar root@120.25.x.x:/root/
```

### Step 2: 服务器更新与重启 (SSH 连接后执行)
在 ECS 服务器上执行，加载新镜像并重启服务。

```bash
# 1. (可选) 更新代码库
# 如果你只改了代码但没改配置，其实这一步不是必须的（因为我们用的是镜像运行）
# 但保持服务器代码最新是个好习惯
cd /opt/novel
git fetch --all
git reset --hard origin/main

# 2. 导入镜像
# -i: 输入文件 (input)
docker load -i /root/novel-backend.tar

# 3. 重建容器
cd /opt/novel/backend

# 先停止并删除旧容器 (保留数据卷)
docker compose down

# 启动新容器 (-d 后台运行)
# 这一步会重新读取 docker-compose.yml 并使用刚刚 load 进来的 latest 镜像
docker compose up -d

# 4. 验证日志
# --tail 100: 只看最后 100 行
# -f: 实时跟随 (follow)
docker compose logs --tail 100 -f backend
```

---

## 3. Android 调试与安装 (ADB)

使用 Android Debug Bridge (ADB) 管理连接的真机或模拟器。

### 查找 ADB 路径
Android Studio 自带 ADB，无需单独下载，但通常未加入环境变量。

**默认安装路径**：
- `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- 对应完整路径示例：`C:\Users\<你的用户名>\AppData\Local\Android\Sdk\platform-tools\adb.exe`

**如何确认本机路径**：
1. 打开 Android Studio
2. 菜单：`File` → `Settings` → `Languages & Frameworks` → `Android SDK`
3. 复制顶部的 `Android SDK Location`
4. 拼上 `\platform-tools\adb.exe` 即为完整路径

### 常用命令 (PowerShell)
以下命令使用环境变量 `%LOCALAPPDATA%` 指代默认路径。如果你的 SDK 在 D 盘或 F 盘，请替换为实际路径（例如 `& "F:\Android\Sdk\platform-tools\adb.exe" ...`）。

```powershell
# 查看连接设备
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices

# 安装 APK (覆盖安装)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "D:\项目\novel\frontend\android\app\build\outputs\apk\debug\app-debug.apk"

# 指定设备安装 (当有多个设备时)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <设备序列号> install -r "..."

# 卸载应用
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" uninstall com.novel.writing.assistant
```

### Android Studio中构建并传参 (Gradle)
**命令含义**：在命令行触发 Gradle 构建，并通过 `-P` 传入参数覆盖 `build.gradle` 里的变量。

**本项目示例**（构建指向 ECS 公网 IP 的 Debug 包）：
```powershell
cd D:\项目\novel\frontend\android

# :app:assembleDebug -> 只构建 app 模块的 debug 变体
# -PAPI_BASE_URL_DEBUG -> 覆盖 debug 环境下的后端地址
# 注意: 地址末尾必须带 /api
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL_DEBUG="http://<ECS公网IP>:8080/api"
```

---

## 4. Docker 常用运维命令详解

### 服务管理
```bash
# 后台启动所有服务
# -d: Detached mode (后台运行)
docker compose up -d

# 停止并移除容器、网络
# (不会删除数据卷 volumes，除非加 -v)
docker compose down

# 重启指定服务 (不重建容器，只重启进程)
# 适用于: 只改了 .env 配置，或者想重启应用
docker compose restart backend

# 查看当前目录下 compose 管理的容器状态
docker compose ps
```

### 日志查看
```bash
# 查看实时滚动日志 (按 Ctrl+C 退出)
docker compose logs -f backend

# 查看最后 200 行日志 (排查启动报错最有用)
docker compose logs --tail 200 backend
```

### 镜像清理
```bash
# 列出本地所有镜像
docker images

# 清理 "悬空" (dangling) 镜像
# 含义: 清理那些没有 tag 的、被新版本覆盖掉的旧镜像层
# -f: 强制执行不确认
docker image prune -f
```

### 容器内调试
**命令含义**：进入正在运行的容器内部执行命令。

**本项目示例**（进入后端容器检查网络连通性）：
```bash
# 1. 进入容器的 shell 环境
# backend-backend-1 是容器名 (可以通过 docker ps 看到)
docker exec -it backend-backend-1 sh

# 2. (在容器内执行) 测试自身接口
# 如果返回 OK，说明服务内部是正常的
curl http://127.0.0.1:8080/health

# 3. 退出容器
exit
```
