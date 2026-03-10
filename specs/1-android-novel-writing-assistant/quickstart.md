# 快速启动指南：安卓小说写作助手应用

**Created**: 2026-02-03  
**Based On**: [Implementation Plan](./plan.md)

## 开发环境设置

### 前端开发环境

#### 系统要求
- **操作系统**: Windows 10/11, macOS, Linux
- **JDK**: JDK 11 或更高版本
- **Android Studio**: Android Studio Hedgehog 或更高版本
- **Android SDK**: API Level 24 或更高版本
- **Kotlin**: Kotlin 1.9.0 或更高版本

#### 环境配置
1. **安装 Android Studio**
   - 从 [Android Studio 官网](https://developer.android.com/studio) 下载并安装
   - 安装过程中选择默认选项，确保安装 Android SDK

2. **配置 Kotlin**
   - Android Studio 会自动安装 Kotlin 插件
   - 确保项目使用 Kotlin 1.9.0 或更高版本

3. **克隆项目仓库**
   ```bash
   git clone https://github.com/your-org/novel-writing-assistant-android.git
   cd novel-writing-assistant-android
   ```

4. **打开项目**
   - 在 Android Studio 中选择 "Open an existing project"
   - 导航到项目目录并选择

5. **同步依赖**
   - Android Studio 会自动检测并提示同步 Gradle 依赖
   - 点击 "Sync Now" 完成依赖同步

### 后端开发环境

#### 系统要求
- **操作系统**: Windows 10/11, macOS, Linux
- **JDK**: JDK 11 或更高版本
- **Kotlin**: Kotlin 1.9.0 或更高版本
- **Docker**: Docker Desktop 或 Docker Engine
- **Kubernetes**: Minikube (本地开发) 或云 Kubernetes 集群 (生产)

#### 环境配置
1. **安装 JDK**
   - 从 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 或 [OpenJDK](https://openjdk.org/) 下载并安装
   - 配置 `JAVA_HOME` 环境变量

2. **安装 Kotlin**
   - 从 [Kotlin 官网](https://kotlinlang.org/docs/installation.html) 下载并安装
   - 或使用 IDE 内置的 Kotlin 支持

3. **安装 Docker**
   - 从 [Docker 官网](https://www.docker.com/products/docker-desktop) 下载并安装 Docker Desktop
   - 启动 Docker 服务

4. **克隆项目仓库**
   ```bash
   git clone https://github.com/your-org/novel-writing-assistant-backend.git
   cd novel-writing-assistant-backend
   ```

5. **构建项目**
   ```bash
   ./gradlew build
   ```

## API密钥配置

### Coze API 密钥
1. **获取 Coze API 密钥**
   - 登录 Coze 开发者平台
   - 创建新的 API 密钥
   - 记录生成的 API 密钥

2. **配置后端服务**
   - 创建 `.env` 文件在后端项目根目录
   - 添加以下配置：
   ```env
   COZE_API_KEY=your_coze_api_key
   COZE_API_BASE_URL=https://api.coze.cn/v1
   ```

3. **配置前端应用**
   - 前端应用不直接存储 API 密钥
   - 前端通过后端服务间接调用 Coze API

### 应用 API 密钥
1. **生成应用 API 密钥**
   - 使用安全的随机生成工具生成 API 密钥
   - 记录生成的 API 密钥

2. **配置后端服务**
   - 在 `.env` 文件中添加：
   ```env
   APP_API_KEY=your_application_api_key
   ```

3. **配置前端应用**
   - 在 `app/src/main/res/values/strings.xml` 中添加：
   ```xml
   <string name="api_key">your_application_api_key</string>
   ```
   - 确保此文件不被提交到版本控制系统

## 测试流程

### 前端测试

#### 单元测试
1. **运行单元测试**
   ```bash
   ./gradlew test
   ```

2. **查看测试报告**
   - 测试报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`
   - 在浏览器中打开查看详细结果

#### 集成测试
1. **运行集成测试**
   ```bash
   ./gradlew connectedAndroidTest
   ```

2. **查看测试报告**
   - 测试报告位于 `app/build/reports/androidTests/connected/index.html`
   - 在浏览器中打开查看详细结果

### 后端测试

#### 单元测试
1. **运行单元测试**
   ```bash
   ./gradlew test
   ```

2. **查看测试报告**
   - 测试报告位于 `build/reports/tests/test/index.html`
   - 在浏览器中打开查看详细结果

#### 集成测试
1. **运行集成测试**
   ```bash
   ./gradlew integrationTest
   ```

2. **查看测试报告**
   - 测试报告位于 `build/reports/tests/integrationTest/index.html`
   - 在浏览器中打开查看详细结果

### 端到端测试

#### 配置测试环境
1. **启动后端服务**
   ```bash
   ./gradlew run
   ```

2. **启动前端应用**
   - 在 Android Studio 中点击 "Run" 按钮
   - 选择目标设备（模拟器或真机）

#### 执行测试
1. **手动测试**
   - 按照 [用户场景](./spec.md#user-scenarios--testing) 执行测试
   - 验证每个功能是否正常工作

2. **自动化测试**
   - 使用 Espresso 或 UI Automator 编写端到端测试
   - 运行测试并查看结果

## 部署流程

### 后端服务部署

#### 构建 Docker 镜像
1. **构建镜像**
   ```bash
   docker build -t novel-writing-assistant-backend:latest .
   ```

2. **推送镜像**
   ```bash
   docker tag novel-writing-assistant-backend:latest your-registry/novel-writing-assistant-backend:latest
   docker push your-registry/novel-writing-assistant-backend:latest
   ```

#### 部署到 Kubernetes
1. **创建 Kubernetes 配置**
   - 编辑 `k8s/deployment.yaml` 文件
   - 配置镜像地址和环境变量

2. **应用配置**
   ```bash
   kubectl apply -f k8s/deployment.yaml
   kubectl apply -f k8s/service.yaml
   ```

3. **验证部署**
   ```bash
   kubectl get pods
   kubectl get services
   ```

### 前端应用部署

#### 构建应用
1. **构建发布版本**
   ```bash
   ./gradlew assembleRelease
   ```

2. **生成签名 APK**
   - 按照 Android Studio 向导生成签名密钥
   - 配置 `build.gradle` 文件中的签名信息
   - 运行 `./gradlew assembleRelease` 生成签名 APK

#### 发布应用
1. **发布到 Google Play**
   - 登录 Google Play Console
   - 创建新的应用版本
   - 上传签名 APK
   - 填写发布信息并提交审核

2. **内部测试**
   - 创建内部测试轨道
   - 上传测试版本
   - 邀请测试人员

## 监控与日志

### 后端监控
1. **配置日志**
   - 使用 SLF4J + Logback 进行日志记录
   - 配置不同环境的日志级别

2. **监控指标**
   - 使用 Micrometer 收集应用指标
   - 集成 Prometheus 和 Grafana 进行监控

3. **错误跟踪**
   - 集成 Sentry 或类似工具进行错误跟踪
   - 配置错误警报

### 前端监控
1. **崩溃报告**
   - 集成 Firebase Crashlytics 或类似工具
   - 配置崩溃报告和警报

2. **性能监控**
   - 使用 Firebase Performance Monitoring 或类似工具
   - 监控应用启动时间、页面加载时间等

3. **用户行为分析**
   - 集成 Google Analytics 或类似工具
   - 分析用户行为和功能使用情况

## 故障排除

### 常见问题

#### 前端问题
1. **应用崩溃**
   - 检查 Crashlytics 崩溃报告
   - 查看设备日志
   - 检查是否有 null 指针或其他异常

2. **网络请求失败**
   - 检查网络连接
   - 检查后端服务是否运行
   - 检查 API 密钥是否正确

3. **UI 显示异常**
   - 检查布局文件
   - 检查设备屏幕尺寸和方向
   - 检查主题和样式配置

#### 后端问题
1. **服务启动失败**
   - 检查端口是否被占用
   - 检查环境变量是否正确
   - 查看应用日志

2. **API 响应错误**
   - 检查 Coze API 密钥是否有效
   - 检查 Coze API 服务状态
   - 查看应用日志中的错误信息

3. **性能问题**
   - 检查数据库查询
   - 检查网络请求
   - 检查内存使用情况

### 调试技巧

#### 前端调试
1. **使用 Android Studio 调试器**
   - 设置断点
   - 运行调试模式
   - 查看变量和调用栈

2. **使用 Logcat**
   - 在 Android Studio 中打开 Logcat 视图
   - 过滤日志级别和标签
   - 查看应用日志输出

#### 后端调试
1. **使用 IDE 调试器**
   - 设置断点
   - 运行调试模式
   - 查看变量和调用栈

2. **使用日志**
   - 添加详细的日志记录
   - 查看应用日志输出
   - 分析请求和响应

## 联系与支持

### 开发团队
- **前端开发**: frontend-team@novel-writing-assistant.com
- **后端开发**: backend-team@novel-writing-assistant.com
- **产品管理**: product-team@novel-writing-assistant.com

### 技术文档
- **API 文档**: https://api.novel-writing-assistant.com/docs
- **开发指南**: https://docs.novel-writing-assistant.com/developers
- **故障排除**: https://docs.novel-writing-assistant.com/troubleshooting

### 支持渠道
- **GitHub Issues**: https://github.com/your-org/novel-writing-assistant/issues
- **Slack Channel**: #novel-writing-assistant-dev
- **Email Support**: support@novel-writing-assistant.com