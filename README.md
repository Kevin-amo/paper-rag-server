# Paper Mind

Paper Mind 是一个面向论文辅助评审场景的智能平台，支持用户端论文上传与问答、评审端结构化解析与多维评分、AI 辅助评语与风险提示、评审组长分配与共识确认、后台用户和角色管理。系统基于 DashScope / Qwen 模型链路，集成 OpenAlex 外部文献检索。

项目包含两部分：

- 后端：Spring Boot 3.5 + Java 21
- 前端：Vue 3 + Vite + Element Plus

## 功能特性

### 用户与认证

- 用户登录、注册、登出和当前用户信息查询
- JWT 无状态鉴权、登录失败锁定、Token 注销
- 邮箱验证码注册（Resend 邮件服务）
- 用户个人设置：修改密码、修改昵称、换绑邮箱
- 用户头像上传（阿里云 OSS / 本地存储自动切换）

### 管理员

- 用户管理：创建用户、修改角色、禁用用户、重置密码、删除用户
- 评审配置：评审批次管理、评审小组与成员管理、评审指标配置
- 评审任务管理：任务查看、评审员分配、共识重新计算与确认
- 评审员工作负载查看

### 文档管理

- 用户端论文上传、批量上传、异步解析入库
- 文档列表、详情、分片、内嵌图片资源查看
- 文档元数据编辑、软删除与恢复
- 文档重建索引
- 论文结构化解析（规则 + 模型双通道，支持重新生成）

### RAG 智能问答

- Agent 编排式 SSE 流式问答
- pgvector 向量存储和相似度检索
- DashScope / Qwen 大模型问答、Embedding 和 Rerank
- 外部文献检索（OpenAlex），带 Redis 缓存和防缓存击穿
- 引用归一化与文献上下文策略

### 论文辅助评审

- 评审任务池与评审员分配
- 论文结构化内容解析（规则解析 + 模型补全 + 合并策略）
- 多维辅助评分（6 项预置指标：政策导向、专业匹配、创新性、逻辑性、语言质量、参考文献规范）
- AI 辅助评语生成、风险提示（参考文献格式检查）
- 风险项全生命周期管理（确认 / 忽略 / 解决）
- 评审报告调整与提交
- 评审共识自动计算与人工确认
- 评审组长工作台：任务分配、报告查看、共识管理
- 评审操作留档（before/after 快照与 diff）

### 基础设施

- Redis、RabbitMQ、PostgreSQL/pgvector 本地 Docker 编排
- Docker Compose 全栈部署（前端 Nginx + 后端 + 基础设施）

## 技术栈

### 后端

| 技术 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.14 |
| Spring Security | (随 Boot 版本) |
| Spring AI | 1.1.5 |
| spring-ai-alibaba-starter-dashscope | 1.1.2.0 |
| MyBatis-Plus | 3.5.16 |
| PostgreSQL + pgvector | 16 |
| Redis | 7.4 |
| RabbitMQ | 3.13 |
| Apache Tika | 2.9.2 |
| Apache PDFBox | 2.0.31 |
| Aliyun OSS SDK | 3.18.5 |
| Maven | 3.9+ |

### 前端

| 技术 | 版本 |
| --- | --- |
| Vue | 3.5 |
| TypeScript | 6.0 |
| Vite | 8.0 |
| Element Plus | 2.13 |
| Vue Router | 5.0 |
| Axios | 1.16 |
| MarkdownIt | 14.1 |
| DOMPurify | 3.4 |

## 目录结构

```text
paper-mind/
├── src/
│   ├── main/
│   │   ├── java/com/lqr/papermind/
│   │   │   ├── agent/           # Agent 编排、工具调用和流式问答入口
│   │   │   ├── ai/              # LLM、Embedding、Rerank、Prompt 构造
│   │   │   ├── auth/            # 认证、用户、角色、JWT、验证码频控
│   │   │   ├── common/          # 通用响应、异常、日志和类型处理
│   │   │   ├── config/          # Spring Security、MyBatis 和应用配置
│   │   │   ├── conversation/    # 会话和消息
│   │   │   ├── document/        # 文档上传、解析、切分、入库、资源管理和结构化解析
│   │   │   ├── literature/      # OpenAlex 外部文献检索、意图解析和缓存
│   │   │   ├── mail/            # Resend 邮件发送配置和服务
│   │   │   ├── rag/             # RAG 检索和回答
│   │   │   ├── review/          # 论文辅助评审任务、评分报告、评审标准、共识和留档
│   │   │   ├── storage/         # 阿里云 OSS 对象存储
│   │   │   └── vector/          # 向量写入
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-local.example.yaml
│   │       └── sql/paper-mind.sql
│   └── test/                    # 单元测试和 Web 层测试
├── frontend/
│   ├── src/
│   │   ├── api/                 # 前端 API 请求封装
│   │   ├── components/          # 页面组件和业务组件
│   │   ├── composables/         # 前端状态和业务组合逻辑
│   │   ├── layouts/             # 页面布局
│   │   ├── router/              # 前端路由
│   │   ├── types/               # TypeScript 类型
│   │   ├── utils/               # 前端工具方法
│   │   └── views/               # 页面视图
│   ├── package.json
│   └── vite.config.ts
├── storage/                     # 本地上传/解析临时文件，默认被 git 忽略
├── docker-compose.yml           # 全栈部署（前端 + 后端 + PostgreSQL + Redis + RabbitMQ）
└── pom.xml
```

## 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+，建议使用较新的 LTS 版本
- npm
- Docker / Docker Compose
- 可用的 DashScope API Key

## 快速启动

### 1. 启动基础设施

在项目根目录运行：

```bash
docker compose up -d
```

该命令会启动：

- PostgreSQL 16 + pgvector：`localhost:5432`
- Redis 7.4：`localhost:6379`
- RabbitMQ 3.13：`localhost:5672`
- RabbitMQ Management：`http://localhost:15672`

默认本地账号见 `docker-compose.yml`。

### 2. 准备本地配置

复制示例配置：

```bash
cp src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
```

Windows PowerShell：

```powershell
Copy-Item src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
```

然后编辑 `src/main/resources/application-local.yaml`，至少配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:your-api-key}
```

也可以直接使用环境变量：

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
```

Linux/macOS：

```bash
export DASHSCOPE_API_KEY="your-api-key"
```

如需初始化管理员账号，可设置：

```yaml
app:
  security:
    bootstrap-admin:
      enabled: true
      username: admin
      password: your-admin-password
      display-name: 系统管理员
```

如需启用邮箱验证码注册，配置 Resend：

```yaml
app:
  mail:
    resend:
      api-key: your-resend-api-key
      from: noreply@your-domain.com
```

如需启用阿里云 OSS 头像存储，配置：

```yaml
app:
  storage:
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      bucket: your-bucket
      access-key-id: your-access-key-id
      access-key-secret: your-access-key-secret
      public-base-url: https://your-bucket.oss-cn-hangzhou.aliyuncs.com
```

> 注意：`application-local*.yaml` 默认被 `.gitignore` 忽略，不要把真实密钥提交到仓库。

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/actuator/health
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 默认会把 `/api/*` 请求代理到 `http://localhost:8080`，并去掉 `/api` 前缀后转发到后端；前端代码默认使用 `VITE_API_PREFIX=/api`，避免 `/admin` 等 SPA 页面路由在浏览器刷新时被代理到后端。

如需修改后端地址：

```powershell
$env:VITE_BACKEND_URL="http://localhost:8080"
npm run dev
```

### 5. Docker Compose 全栈部署

`docker-compose.yml` 支持一键部署完整应用（前端 + 后端 + 基础设施）：

```bash
docker compose --profile full up -d --build
```

必须设置的环境变量：

| 变量 | 说明 |
| --- | --- |
| `POSTGRES_PASSWORD` | PostgreSQL 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 |
| `DASHSCOPE_API_KEY` | DashScope API Key |
| `JWT_SECRET` | JWT 签名密钥（生产环境务必使用强密钥） |

可通过 `.env` 文件或环境变量传入。前端通过 Nginx 反向代理 `/api/` 到后端，默认对外暴露 `${HTTP_PORT:-80}` 端口。

## 常用命令

### 后端测试

```bash
mvn test
```

### 后端打包

```bash
mvn clean package
```

### 前端类型检查和构建

```bash
cd frontend
npm run build
```

### 停止基础设施

```bash
docker compose down
```

如需清理数据卷：

```bash
docker compose down -v
```

## 主要配置项

项目主配置位于：

```text
src/main/resources/application.yaml
```

本地覆盖配置建议放在：

```text
src/main/resources/application-local.yaml
```

### 基础环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 后端端口 | `8080` |
| `DB_URL` | PostgreSQL JDBC 地址 | `jdbc:postgresql://localhost:5432/mydatabase` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `REDIS_DATABASE` | Redis 数据库编号 | `0` |
| `RABBITMQ_HOST` | RabbitMQ 地址 | `192.168.200.129` |
| `RABBITMQ_PORT` | RabbitMQ 端口 | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | `admin` |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | `admin` |
| `DASHSCOPE_API_KEY` | DashScope API Key | 空 |
| `JWT_SECRET` | JWT 签名密钥 | 本地开发默认值 |

### AI 模型配置

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `spring.ai.dashscope.chat.options.model` | 对话模型 | `qwen3.6-27b` |
| `spring.ai.dashscope.chat.options.temperature` | 温度 | `0.2` |
| `spring.ai.dashscope.chat.options.max-tokens` | 最大输出 token | `6144` |
| `spring.ai.dashscope.chat.options.enable-thinking` | 启用思考模式 | `false` |
| `spring.ai.dashscope.embedding.options.model` | Embedding 模型 | `text-embedding-v4` |
| `spring.ai.dashscope.embedding.options.dimensions` | Embedding 维度 | `1536` |

### PgVector 配置

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `spring.ai.vectorstore.pgvector.dimensions` | 向量维度 | `1536` |
| `spring.ai.vectorstore.pgvector.index-type` | 索引类型 | `hnsw` |
| `spring.ai.vectorstore.pgvector.distance-type` | 距离类型 | `cosine_distance` |
| `spring.ai.vectorstore.pgvector.table-name` | 表名 | `vector_store` |

### RAG 配置 (`app.rag`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.rag.chunk-size` | 单个文本片段最大字符数 | `800` |
| `app.rag.chunk-overlap` | 相邻片段重叠字符数 | `120` |
| `app.rag.default-top-k` | 问答检索默认召回片段数 | `3` |
| `app.rag.similarity-threshold` | 向量检索相似度阈值 | `0` |
| `app.rag.rerank.enabled` | 是否启用精排序 | `true` |
| `app.rag.rerank.model` | 精排序模型 | `qwen3-rerank` |
| `app.rag.rerank.top-n` | 精排序返回数量 | `5` |
| `app.rag.rerank.candidate-multiplier` | 候选倍数 | `3` |
| `app.rag.rerank.timeout` | 精排序超时 | `10s` |

### 文献检索配置 (`app.literature.search`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.literature.search.openalex.enabled` | 启用 OpenAlex 搜索 | `true` |
| `app.literature.search.openalex.endpoint` | OpenAlex API 地址 | `https://api.openalex.org/works` |
| `app.literature.search.openalex.timeout` | 请求超时 | `10s` |
| `app.literature.search.openalex.mailto` | 联系邮箱（Polite Pool） | 空 |
| `app.literature.search.cache.enabled` | 启用缓存 | `true` |
| `app.literature.search.cache.ttl` | 缓存过期时间 | `20m` |

### 文档入库配置 (`app.document-ingestion`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.document-ingestion.storage-dir` | 上传文件存储目录 | `storage/document-ingestion` |
| `app.document-ingestion.keep-upload-file` | 入库后保留原始文件 | `false` |
| `app.document-ingestion.max-retry-count` | 最大重试次数 | `3` |
| `app.document-ingestion.listener.concurrency` | MQ 监听器初始并发 | `2` |
| `app.document-ingestion.listener.max-concurrency` | MQ 监听器最大并发 | `4` |
| `app.document-ingestion.cleanup.enabled` | 启用过期文件清理 | `true` |
| `app.document-ingestion.cleanup.retention` | 文件保留时长 | `24h` |

### 安全配置 (`app.security`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.security.jwt.access-token-ttl` | 访问令牌有效期 | `12h` |
| `app.security.login-attempt.enabled` | 启用登录失败锁定 | `true` |
| `app.security.login-attempt.max-failures` | 最大失败次数 | `5` |
| `app.security.login-attempt.window` | 失败计数窗口 | `10m` |
| `app.security.login-attempt.lock-duration` | 锁定时长 | `10m` |
| `app.security.register-email-code.code-ttl` | 验证码有效期 | `5m` |
| `app.security.register-email-code.email-cooldown` | 同一邮箱发送冷却 | `60s` |
| `app.security.register-email-code.email-daily-limit` | 每邮箱每日上限 | `10` |
| `app.security.register-email-code.ip-minute-limit` | 每 IP 每分钟上限 | `20` |
| `app.security.register-email-code.ip-daily-limit` | 每 IP 每日上限 | `200` |

### 对象存储配置 (`app.storage.oss`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.storage.oss.endpoint` | 阿里云 OSS Endpoint | 空 |
| `app.storage.oss.bucket` | OSS Bucket | 空 |
| `app.storage.oss.access-key-id` | AccessKey ID | 空 |
| `app.storage.oss.access-key-secret` | AccessKey Secret | 空 |
| `app.storage.oss.public-base-url` | 公共访问 URL | 空 |
| `app.storage.oss.avatar-prefix` | 头像存储前缀 | `avatars` |
| `app.storage.oss.avatar-max-size` | 头像最大大小 | `5MB` |

> OSS 未配置时，头像存储自动回退到本地文件系统。

### 邮件配置 (`app.mail.resend`)

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `app.mail.resend.enabled` | 启用 Resend 邮件 | `true` |
| `app.mail.resend.api-key` | Resend API 密钥 | 空 |
| `app.mail.resend.from` | 发件人地址 | 空 |
| `app.mail.resend.timeout` | 请求超时 | `10s` |

> 生产部署时务必显式配置 `JWT_SECRET`、数据库密码、RabbitMQ 密码和 DashScope API Key，不要使用开发默认值。

## API 简览

所有非公开接口默认需要请求头：

```http
Authorization: Bearer <access-token>
```

### 认证 (`/auth`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/login` | 登录 |
| `POST` | `/auth/register/email-code` | 创建注册邮箱验证码 |
| `POST` | `/auth/register` | 使用邮箱验证码注册 |
| `GET` | `/auth/me` | 当前用户信息 |
| `POST` | `/auth/me/avatar` | 上传头像 |
| `POST` | `/auth/me/password` | 修改密码 |
| `POST` | `/auth/me/display-name` | 修改昵称 |
| `POST` | `/auth/me/email-code` | 发送换绑邮箱验证码 |
| `POST` | `/auth/me/email` | 换绑邮箱 |
| `POST` | `/auth/logout` | 登出 |

注册邮箱验证码发送会进行 Redis 频控：同一邮箱默认 `60s` 冷却、24 小时窗口内最多 `10` 次；同一 IP 默认每分钟最多 `20` 次、24 小时窗口内最多 `200` 次。触发上限时接口返回 `429 TOO_MANY_REQUESTS`。

### 文档 (`/documents`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/documents` | 上传单个文档，返回异步入库任务 |
| `POST` | `/documents/batch` | 批量上传文档 |
| `GET` | `/documents` | 分页查询文档列表 |
| `GET` | `/documents/jobs/{jobId}` | 查询入库任务 |
| `GET` | `/documents/{sourceId}` | 查询文档详情 |
| `GET` | `/documents/{sourceId}/structured-parse` | 查询论文结构化解析结果 |
| `GET` | `/documents/{sourceId}/structured-parse/status` | 查询结构化解析状态 |
| `POST` | `/documents/{sourceId}/structured-parse/regenerate` | 重新生成结构化解析 |
| `GET` | `/documents/{sourceId}/chunks` | 查询文档分片 |
| `GET` | `/documents/{sourceId}/assets` | 查询文档资源 |
| `GET` | `/documents/{sourceId}/assets/{assetId}/content` | 下载资源内容 |
| `PATCH` | `/documents/{sourceId}/metadata` | 更新文档元数据 |
| `DELETE` | `/documents` | 删除当前用户全部文档 |
| `DELETE` | `/documents/{sourceId}` | 删除指定文档 |
| `POST` | `/documents/{sourceId}/restore` | 恢复文档 |
| `POST` | `/documents/{sourceId}/reindex` | 重建索引 |

### Agent 问答 (`/agent`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/agent/ask/stream` | Agent SSE 流式问答 |

### 会话 (`/conversations`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/conversations` | 查询会话列表 |
| `POST` | `/conversations` | 创建会话 |
| `PATCH` | `/conversations/{conversationId}` | 更新会话 |
| `GET` | `/conversations/{conversationId}/messages` | 查询会话消息 |
| `DELETE` | `/conversations/{conversationId}` | 删除会话 |

### 评审 (`/reviews`)

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/reviews/upload` | REVIEWER | 上传评审论文 |
| `GET` | `/reviews/tasks` | REVIEWER | 分页查询评审任务列表 |
| `POST` | `/reviews/tasks` | - | 创建评审任务 |
| `GET` | `/reviews/tasks/{taskId}` | REVIEWER | 获取评审任务详情 |
| `GET` | `/reviews/tasks/{taskId}/consensus` | REVIEWER | 获取评审任务共识 |
| `PATCH` | `/reviews/tasks/{taskId}/consensus` | REVIEWER | 更新评审任务共识 |
| `POST` | `/reviews/tasks/{taskId}/consensus/confirm` | REVIEWER | 确认评审任务共识 |
| `POST` | `/reviews/tasks/{taskId}/ai-review` | REVIEWER | 生成 AI 评审报告 |
| `GET` | `/reviews/tasks/{taskId}/structured-parse` | REVIEWER | 获取论文结构化解析 |
| `POST` | `/reviews/tasks/{taskId}/structured-parse/regenerate` | REVIEWER | 重新生成结构化解析 |
| `PATCH` | `/reviews/reports/{reportId}` | REVIEWER | 更新评审报告 |
| `POST` | `/reviews/assignments/{assignmentId}/submit` | REVIEWER | 提交评审分配 |
| `GET` | `/reviews/reports/{reportId}/risks` | REVIEWER | 获取评审报告风险列表 |
| `PUT` | `/reviews/risks/{riskId}` | REVIEWER | 更新风险项 |
| `POST` | `/reviews/risks/{riskId}/confirm` | REVIEWER | 确认风险项 |
| `POST` | `/reviews/risks/{riskId}/ignore` | REVIEWER | 忽略风险项 |
| `POST` | `/reviews/risks/{riskId}/resolve` | REVIEWER | 解决风险项 |
| `GET` | `/reviews/criteria` | REVIEWER | 获取评审标准列表 |
| `POST` | `/reviews/criteria` | ADMIN | 创建评审标准 |
| `PATCH` | `/reviews/criteria/{id}` | ADMIN | 更新评审标准 |

### 管理员 - 评审任务 (`/admin/reviews`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/reviews/tasks` | 分页查询评审任务列表 |
| `GET` | `/admin/reviews/tasks/{taskId}` | 获取评审任务详情 |
| `POST` | `/admin/reviews/tasks/{taskId}/assignments` | 分配评审员 |
| `POST` | `/admin/reviews/tasks/{taskId}/consensus/recalculate` | 重新计算评审共识 |
| `PATCH` | `/admin/reviews/tasks/{taskId}/consensus` | 更新评审共识 |
| `POST` | `/admin/reviews/tasks/{taskId}/consensus/confirm` | 确认评审共识 |
| `GET` | `/admin/reviews/reviewer-loads` | 获取评审员工作负载 |

### 管理员 - 评审配置 (`/admin/reviews`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/reviews/batches` | 分页查询评审批次列表 |
| `POST` | `/admin/reviews/batches` | 创建评审批次 |
| `PATCH` | `/admin/reviews/batches/{batchId}` | 更新评审批次 |
| `GET` | `/admin/reviews/groups` | 获取评审组列表 |
| `POST` | `/admin/reviews/groups` | 创建评审组 |
| `PATCH` | `/admin/reviews/groups/{groupId}` | 更新评审组 |
| `GET` | `/admin/reviews/groups/{groupId}/members` | 获取评审组成员列表 |
| `PUT` | `/admin/reviews/groups/{groupId}/members` | 替换评审组成员 |

### 评审组长 (`/review-leader`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/review-leader/groups` | 获取当前用户负责的评审组列表 |
| `GET` | `/review-leader/groups/{groupId}/members` | 获取评审组成员列表 |
| `GET` | `/review-leader/groups/{groupId}/tasks` | 获取评审组任务列表 |
| `GET` | `/review-leader/groups/{groupId}/tasks/unassigned` | 获取未分配的任务列表 |
| `POST` | `/review-leader/groups/{groupId}/tasks/{taskId}/assignments` | 分配评审任务给评审员 |
| `GET` | `/review-leader/groups/{groupId}/tasks/{taskId}/reports` | 获取评审任务报告列表 |
| `GET` | `/review-leader/groups/{groupId}/tasks/{taskId}/consensus` | 获取评审任务共识 |
| `POST` | `/review-leader/groups/{groupId}/tasks/{taskId}/consensus/recalculate` | 重新计算评审共识 |
| `PATCH` | `/review-leader/groups/{groupId}/tasks/{taskId}/consensus` | 更新评审共识 |
| `POST` | `/review-leader/groups/{groupId}/tasks/{taskId}/consensus/confirm` | 确认评审共识 |

### 管理员 - 用户管理 (`/admin/users`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/users` | 查询用户列表 |
| `POST` | `/admin/users` | 创建用户 |
| `PATCH` | `/admin/users/{id}` | 更新用户基础信息 |
| `PATCH` | `/admin/users/{id}/roles` | 更新用户角色 |
| `PATCH` | `/admin/users/{id}/status` | 更新用户状态 |
| `POST` | `/admin/users/{id}/reset-password` | 重置密码 |
| `DELETE` | `/admin/users/{id}` | 删除用户 |

## 角色体系

| 角色 | 代码 | 说明 |
| --- | --- | --- |
| 管理员 | `ADMIN` | 用户管理、评审配置、评审任务管理 |
| 普通用户 | `USER` | 文档管理与 RAG 问答 |
| 评审员 | `REVIEWER` | 论文辅助评审、评审报告调整、评审组长工作台 |

## 评审工作流

### 任务状态

```text
PENDING_ASSIGNMENT → ASSIGNED → IN_REVIEW → SUBMITTED → CONSENSUS_CONFIRMED / NEEDS_REVIEW
```

### 评审报告状态

```text
AI_GENERATED → ADJUSTED → CONFIRMED → COMPLETED
```

### 共识状态

```text
DRAFT → IN_DISCUSSION → CONFIRMED → ARCHIVED
```

### 风险项状态

```text
OPEN → CONFIRMED / IGNORED / RESOLVED
```

### 预置评审指标

| 指标 | 代码 | 权重 | 分类 |
| --- | --- | --- | --- |
| 政策导向 | `POLICY` | 20 | CONTENT |
| 专业匹配 | `MATCH` | 20 | CONTENT |
| 创新性 | `INNOVATION` | 20 | QUALITY |
| 逻辑性 | `LOGIC` | 20 | QUALITY |
| 语言质量 | `LANGUAGE` | 20 | QUALITY |
| 参考文献规范 | `REFERENCE` | 10 | FORMAT |

## 数据库初始化

`docker-compose.yml` 会把以下目录挂载到 PostgreSQL 初始化目录：

```text
src/main/resources/sql
```

首次创建容器数据卷时会执行：

```text
src/main/resources/sql/paper-mind.sql
```

如果 SQL 已修改但数据库卷已经存在，需要清理卷后重新初始化：

```bash
docker compose down -v
docker compose up -d
```
