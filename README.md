# Paper RAG Server

Paper RAG Server 是一个面向论文/文档知识库的 RAG 问答系统，支持用户认证、文档上传、异步解析入库、向量检索、重排序、流式问答和后台用户管理。

项目包含两部分：

- 后端：Spring Boot 3 + Java 21
- 前端：Vue 3 + Vite + Element Plus

## 功能特性

- 用户登录、注册、登出和当前用户信息查询
- JWT 鉴权、登录失败锁定、Token 注销
- 管理员用户管理：创建用户、修改角色、禁用用户、重置密码
- 文档上传、批量上传、异步入库任务查询
- 文档解析、切分、元数据提取和持久化
- pgvector 向量存储和相似度检索
- DashScope 大模型问答、Embedding 和 Rerank
- RAG 普通问答和 SSE 流式问答
- 文档列表、详情、分片、内嵌图片资源查看
- Redis、RabbitMQ、PostgreSQL/pgvector 本地 Docker 编排

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5.x
- Spring Security
- Spring AI / DashScope
- MyBatis-Plus
- PostgreSQL + pgvector
- Redis
- RabbitMQ
- Apache Tika / PDFBox
- Maven

### 前端

- Vue 3
- TypeScript
- Vite
- Element Plus
- Axios
- MarkdownIt + DOMPurify

## 目录结构

```text
paper-rag-server/
├── src/
│   ├── main/
│   │   ├── java/com/lqr/paperragserver/
│   │   │   ├── ai/              # LLM、Embedding、Rerank、Prompt 构造
│   │   │   ├── auth/            # 认证、用户、角色、JWT
│   │   │   ├── config/          # Spring 配置和应用配置属性
│   │   │   ├── conversation/    # 会话和消息
│   │   │   ├── document/        # 文档上传、解析、切分、入库
│   │   │   ├── paper/           # 文档持久化、任务、元数据
│   │   │   ├── rag/             # RAG 检索和回答
│   │   │   ├── storage/         # 对象存储
│   │   │   ├── vector/          # 向量写入
│   │   │   └── web/             # REST API 控制器
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-local.example.yaml
│   │       └── sql/paper-rag.sql
│   └── test/                    # 单元测试和 Web 层测试
├── frontend/                    # Vue 前端项目
├── storage/                     # 本地上传/解析临时文件，默认被 git 忽略
├── docker-compose.yml           # PostgreSQL、Redis、RabbitMQ
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

- PostgreSQL + pgvector：`localhost:5432`
- Redis：`localhost:6379`
- RabbitMQ：`localhost:5672`
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

Vite 默认会把 `/auth`、`/documents`、`/agent`、`/conversations`、`/admin` 等请求代理到 `http://localhost:8080`。

如需修改后端地址：

```powershell
$env:VITE_BACKEND_URL="http://localhost:8080"
npm run dev
```

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

常用环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 后端端口 | `8080` |
| `DB_URL` | PostgreSQL JDBC 地址 | `jdbc:postgresql://localhost:5432/mydatabase` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `RABBITMQ_HOST` | RabbitMQ 地址 | `192.168.200.129` |
| `RABBITMQ_PORT` | RabbitMQ 端口 | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | `admin` |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | `admin` |
| `DASHSCOPE_API_KEY` | DashScope API Key | 空 |
| `JWT_SECRET` | JWT 签名密钥 | 本地开发默认值 |
| `DOCUMENT_INGESTION_STORAGE_DIR` | 文档上传临时目录 | `storage/document-ingestion` |
| `RAG_DEFAULT_TOP_K` | 默认召回数量 | `3` |
| `RAG_RERANK_ENABLED` | 是否启用重排序 | `true` |

> 生产部署时务必显式配置 `JWT_SECRET`、数据库密码、RabbitMQ 密码和 DashScope API Key，不要使用开发默认值。

## API 简览

所有非公开接口默认需要请求头：

```http
Authorization: Bearer <access-token>
```

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/auth/login` | 登录 |
| `POST` | `/auth/register/email-code` | 创建注册邮箱验证码 |
| `POST` | `/auth/register` | 使用邮箱验证码注册 |
| `GET` | `/auth/me` | 当前用户信息 |
| `POST` | `/auth/me/avatar` | 上传头像 |
| `POST` | `/auth/logout` | 登出 |

### 文档

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/documents` | 上传单个文档，返回异步入库任务 |
| `POST` | `/documents/batch` | 批量上传文档 |
| `GET` | `/documents` | 分页查询文档列表 |
| `GET` | `/documents/jobs/{jobId}` | 查询入库任务 |
| `GET` | `/documents/{sourceId}` | 查询文档详情 |
| `GET` | `/documents/{sourceId}/chunks` | 查询文档分片 |
| `GET` | `/documents/{sourceId}/assets` | 查询文档资源 |
| `GET` | `/documents/{sourceId}/assets/{assetId}/content` | 下载资源内容 |
| `PATCH` | `/documents/{sourceId}/metadata` | 更新文档元数据 |
| `DELETE` | `/documents/{sourceId}` | 删除文档 |
| `POST` | `/documents/{sourceId}/restore` | 恢复文档 |
| `POST` | `/documents/{sourceId}/reindex` | 重建索引 |

### Agent 问答

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/agent/ask/stream` | Agent SSE 流式问答 |

### 会话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/conversations` | 查询会话列表 |
| `POST` | `/conversations` | 创建会话 |
| `PATCH` | `/conversations/{conversationId}` | 更新会话 |
| `GET` | `/conversations/{conversationId}/messages` | 查询会话消息 |
| `DELETE` | `/conversations/{conversationId}` | 删除会话 |

### 管理员

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/users` | 查询用户列表 |
| `POST` | `/admin/users` | 创建用户 |
| `PATCH` | `/admin/users/{id}` | 更新用户基础信息 |
| `PATCH` | `/admin/users/{id}/roles` | 更新用户角色 |
| `PATCH` | `/admin/users/{id}/status` | 更新用户状态 |
| `POST` | `/admin/users/{id}/reset-password` | 重置密码 |
| `DELETE` | `/admin/users/{id}` | 删除用户 |

## 数据库初始化

`docker-compose.yml` 会把以下目录挂载到 PostgreSQL 初始化目录：

```text
src/main/resources/sql
```

首次创建容器数据卷时会执行：

```text
src/main/resources/sql/paper-rag.sql
```

如果 SQL 已修改但数据库卷已经存在，需要清理卷后重新初始化：

```bash
docker compose down -v
docker compose up -d
```
