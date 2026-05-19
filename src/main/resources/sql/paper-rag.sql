-- Paper RAG aggregated SQL script.
-- This file concatenates all SQL scripts from `src/main/resources/sql` in dependency-safe execution order.
-- It is meant for one-shot initialization or manual execution in a single import.

-- ===== 00_extensions.sql =====
-- PostgreSQL 扩展初始化脚本。
-- pgvector 用于存储向量；uuid-ossp 用于生成 UUID。

create extension if not exists vector;
create extension if not exists "uuid-ossp";

-- ===== 05_sys_role.sql =====
-- 系统角色表。

create extension if not exists "uuid-ossp";

create table if not exists public.sys_role (
    id uuid primary key default uuid_generate_v4(),
    code varchar(32) not null unique,
    name varchar(64) not null,
    description varchar(255),
    created_at timestamptz not null default now(),

    constraint chk_sys_role_code check (code in ('ADMIN', 'USER'))
);

comment on table public.sys_role is '系统角色表';
comment on column public.sys_role.code is '角色编码：ADMIN、USER';

insert into public.sys_role (code, name, description)
values
    ('ADMIN', '管理员', '拥有文档管理和用户管理权限'),
    ('USER', '普通用户', '拥有文档读取和 RAG 问答权限')
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description;

-- ===== 06_sys_user.sql =====
-- 系统用户表。

create extension if not exists "uuid-ossp";

create table if not exists public.sys_user (
    id uuid primary key default uuid_generate_v4(),
    username varchar(64) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(128),
    email varchar(255),
    phone varchar(32),
    avatar_object_key varchar(512),
    avatar_updated_at timestamptz,
    status varchar(32) not null default 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint chk_sys_user_status check (status in ('ACTIVE', 'DISABLED'))
);

comment on table public.sys_user is '系统用户表';
comment on column public.sys_user.username is '登录用户名';
comment on column public.sys_user.password_hash is 'BCrypt 密码哈希';
comment on column public.sys_user.email is '邮箱，注册登录通道之一';
comment on column public.sys_user.phone is '手机号，预留给后续手机号验证码登录';
comment on column public.sys_user.avatar_object_key is '用户头像在对象存储中的 object key';
comment on column public.sys_user.avatar_updated_at is '用户头像最近更新时间';
comment on column public.sys_user.status is '用户状态：ACTIVE、DISABLED';

create unique index if not exists uk_sys_user_email
    on public.sys_user using btree (lower(email))
    where email is not null;

create unique index if not exists uk_sys_user_phone
    on public.sys_user using btree (phone)
    where phone is not null;

create index if not exists idx_sys_user_status
    on public.sys_user using btree (status);

create index if not exists idx_sys_user_created_at
    on public.sys_user using btree (created_at desc);

-- ===== 07_sys_user_role.sql =====
-- 用户角色关联表。

create table if not exists public.sys_user_role (
    user_id uuid not null,
    role_id uuid not null,
    created_at timestamptz not null default now(),

    primary key (user_id, role_id),
    constraint fk_sys_user_role_user foreign key (user_id) references public.sys_user (id) on delete cascade,
    constraint fk_sys_user_role_role foreign key (role_id) references public.sys_role (id) on delete cascade
);

comment on table public.sys_user_role is '系统用户与角色关联表';

create index if not exists idx_sys_user_role_role_id
    on public.sys_user_role using btree (role_id);

-- ===== 08_conversation.sql =====
-- Agent 会话表。

create extension if not exists "uuid-ossp";

create table if not exists public.conversation (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    title varchar(160) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

comment on table public.conversation is 'RAG Agent 用户会话表';
comment on column public.conversation.owner_user_id is '会话归属用户 ID';
comment on column public.conversation.title is '会话标题';

create index if not exists idx_conversation_owner_updated_at
    on public.conversation using btree (owner_user_id, updated_at desc)
    where deleted_at is null;

create index if not exists idx_conversation_owner_id
    on public.conversation using btree (owner_user_id, id)
    where deleted_at is null;

-- ===== 09_conversation_message.sql =====
-- Agent 会话消息表。

create extension if not exists "uuid-ossp";

create table if not exists public.conversation_message (
    id uuid primary key default uuid_generate_v4(),
    conversation_id uuid not null references public.conversation(id) on delete cascade,
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    role varchar(16) not null,
    message_order integer not null,
    content text not null,
    citations jsonb,
    created_at timestamptz not null default now(),

    constraint chk_conversation_message_role check (role in ('USER', 'ASSISTANT')),
    constraint uk_conversation_message_order unique (conversation_id, message_order)
);

comment on table public.conversation_message is 'RAG Agent 会话消息表';
comment on column public.conversation_message.role is '消息角色：USER、ASSISTANT';
comment on column public.conversation_message.citations is 'Assistant 回答引用信息 JSON';

create index if not exists idx_conversation_message_owner_conversation_order
    on public.conversation_message using btree (owner_user_id, conversation_id, message_order);

create index if not exists idx_conversation_message_conversation_created_at
    on public.conversation_message using btree (conversation_id, created_at);

-- ===== 10_sys_user_avatar.sql =====
-- 为已有系统用户表补充头像字段。

alter table if exists public.sys_user
    add column if not exists avatar_object_key varchar(512),
    add column if not exists avatar_updated_at timestamptz;

comment on column public.sys_user.avatar_object_key is '用户头像在对象存储中的 object key';
comment on column public.sys_user.avatar_updated_at is '用户头像最近更新时间';

-- ===== 01_vector_store.sql =====
-- Spring AI pgvector 向量表。
-- 表名需要和 application.yaml 中的 spring.ai.vectorstore.pgvector.table-name 保持一致。
-- 维度固定为 DashScope text-embedding-v4 使用的 1536，避免运行时向量维度不匹配。

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store
(
    id       uuid NOT NULL DEFAULT uuid_generate_v4(),
    content  text,
    metadata json,
    embedding public.vector(1536),
    CONSTRAINT vector_store_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON public.vector_store
    USING hnsw (embedding public.vector_cosine_ops);

create index if not exists vector_store_owner_user_id_idx
    on public.vector_store ((metadata ->> 'ownerUserId'));

CREATE INDEX IF NOT EXISTS vector_store_source_id_idx
    ON public.vector_store ((metadata ->> 'sourceId'));

CREATE INDEX IF NOT EXISTS vector_store_chunk_id_idx
    ON public.vector_store ((metadata ->> 'chunkId'));

-- ===== 02_paper_document.sql =====
-- 论文/文档主表。
-- 该表保存文档级元数据和入库状态；向量内容仍由 public.vector_store 保存。

create extension if not exists "uuid-ossp";

create table if not exists public.paper_document (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    source_id varchar(128) not null,

    title varchar(512) not null,
    origin varchar(512),
    file_name varchar(255),
    file_type varchar(128),
    file_size bigint,

    authors jsonb,
    abstract text,
    doi varchar(128),
    journal varchar(255),
    publish_year int,
    keywords jsonb,

    content_text text,
    metadata jsonb not null default '{}'::jsonb,

    status varchar(32) not null default 'PENDING',
    chunk_count int not null default 0,
    error_message text,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,

    constraint chk_paper_document_status check (status in ('PENDING', 'QUEUED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'INDEXED', 'FAILED', 'DELETED')),
    constraint chk_paper_document_publish_year check (publish_year is null or publish_year between 1500 and 3000),
    constraint chk_paper_document_chunk_count check (chunk_count >= 0)
);

comment on table public.paper_document is '论文/文档主表，保存文档级元数据和入库状态';
comment on column public.paper_document.owner_user_id is '文档归属用户 ID';
comment on column public.paper_document.source_id is '文档来源唯一标识，对应代码中的 DocumentSource.sourceId';
comment on column public.paper_document.content_text is '解析后的全文文本，便于重建索引和问题排查';
comment on column public.paper_document.metadata is '文档级扩展元数据';
comment on column public.paper_document.status is '入库状态：PENDING、QUEUED、PARSING、CHUNKING、EMBEDDING、INDEXING、INDEXED、FAILED、DELETED';

create index if not exists idx_paper_document_owner_updated_at
    on public.paper_document using btree (owner_user_id, updated_at desc)
    where deleted_at is null;

create unique index if not exists uq_paper_document_owner_source
    on public.paper_document using btree (owner_user_id, source_id);

create index if not exists idx_paper_document_title
    on public.paper_document using btree (title);

create index if not exists idx_paper_document_status
    on public.paper_document using btree (status);

create index if not exists idx_paper_document_publish_year
    on public.paper_document using btree (publish_year);

create index if not exists idx_paper_document_created_at
    on public.paper_document using btree (created_at desc);

create index if not exists idx_paper_document_metadata
    on public.paper_document using gin (metadata);

create index if not exists idx_paper_document_authors
    on public.paper_document using gin (authors);

create index if not exists idx_paper_document_keywords
    on public.paper_document using gin (keywords);

-- ===== 03_paper_document_chunk.sql =====
-- 文档切分片段表。
-- 该表保存 chunk 的原始文本和位置信息，用于引用回溯、重建向量索引和审计。

create extension if not exists "uuid-ossp";

create table if not exists public.paper_document_chunk (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    chunk_id varchar(128) not null,
    source_id varchar(128) not null,

    chunk_index int not null,
    content text not null,
    content_hash varchar(64),

    chunk_start int,
    chunk_end int,
    page_number int,
    section_title varchar(512),
    metadata jsonb not null default '{}'::jsonb,

    vector_store_id uuid,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint fk_paper_document_chunk_source
        foreign key (owner_user_id, source_id)
        references public.paper_document (owner_user_id, source_id)
        on update cascade
        on delete cascade,

    constraint fk_paper_document_chunk_vector_store
        foreign key (vector_store_id)
        references public.vector_store (id)
        on update cascade
        on delete set null,

    constraint uq_paper_document_chunk_owner_chunk unique (owner_user_id, chunk_id),
    constraint uq_paper_document_chunk_source_index unique (owner_user_id, source_id, chunk_index),
    constraint chk_paper_document_chunk_index check (chunk_index >= 0),
    constraint chk_paper_document_chunk_range check (
        chunk_start is null
        or chunk_end is null
        or chunk_end >= chunk_start
    )
);

comment on table public.paper_document_chunk is '文档切分片段表，保存 chunk 原文、位置和向量表关联';
comment on column public.paper_document_chunk.owner_user_id is '分块归属用户 ID';
comment on column public.paper_document_chunk.chunk_id is '片段唯一标识，对应代码中的 DocumentChunk.chunkId';
comment on column public.paper_document_chunk.source_id is '所属文档来源标识，对应 paper_document.source_id';
comment on column public.paper_document_chunk.vector_store_id is '关联 vector_store.id，用于定位向量记录';
comment on column public.paper_document_chunk.metadata is '片段级扩展元数据，例如页码、章节、字符范围等';

create index if not exists idx_paper_document_chunk_owner_source_id
    on public.paper_document_chunk using btree (owner_user_id, source_id);

create index if not exists idx_paper_document_chunk_source_id
    on public.paper_document_chunk using btree (source_id);

create index if not exists idx_paper_document_chunk_vector_store_id
    on public.paper_document_chunk using btree (vector_store_id);

create index if not exists idx_paper_document_chunk_metadata
    on public.paper_document_chunk using gin (metadata);

-- ===== 04_paper_document_asset.sql =====
-- 文档资产表。
-- 该表保存 Word 内嵌图片等二进制资产，用于前端预览和引用回溯。

create extension if not exists "uuid-ossp";

create table if not exists public.paper_document_asset (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    asset_id varchar(128) not null,
    source_id varchar(128) not null,

    asset_index int not null,
    asset_type varchar(32) not null,
    file_name varchar(255),
    content_type varchar(128),
    file_size bigint not null default 0,
    content_hash varchar(64),
    content bytea not null,
    extracted_text text,

    text_start int,
    text_end int,
    metadata jsonb not null default '{}'::jsonb,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint fk_paper_document_asset_source
        foreign key (owner_user_id, source_id)
        references public.paper_document (owner_user_id, source_id)
        on update cascade
        on delete cascade,

    constraint uq_paper_document_asset_owner_asset unique (owner_user_id, asset_id),
    constraint uq_paper_document_asset_source_index unique (owner_user_id, source_id, asset_index),
    constraint chk_paper_document_asset_index check (asset_index >= 0),
    constraint chk_paper_document_asset_size check (file_size >= 0),
    constraint chk_paper_document_asset_type check (asset_type in ('IMAGE', 'ATTACHMENT')),
    constraint chk_paper_document_asset_text_range check (
        text_start is null
        or text_end is null
        or text_end >= text_start
    )
);

comment on table public.paper_document_asset is '文档资产表，保存图片等二进制内容及其 OCR 文本';
comment on column public.paper_document_asset.owner_user_id is '资产归属用户 ID';
comment on column public.paper_document_asset.asset_id is '资产唯一标识，对应代码中的 DocumentAsset.assetId';
comment on column public.paper_document_asset.source_id is '所属文档来源标识，对应 paper_document.source_id';
comment on column public.paper_document_asset.content is '资产二进制内容，用于预览或下载';
comment on column public.paper_document_asset.extracted_text is '从资产中抽取出的文本，例如图片 OCR 结果';
comment on column public.paper_document_asset.metadata is '资产级扩展元数据，例如原始包内路径';

create index if not exists idx_paper_document_asset_owner_source_id
    on public.paper_document_asset using btree (owner_user_id, source_id);

create index if not exists idx_paper_document_asset_source_id
    on public.paper_document_asset using btree (source_id);

create index if not exists idx_paper_document_asset_type
    on public.paper_document_asset using btree (asset_type);

create index if not exists idx_paper_document_asset_metadata
    on public.paper_document_asset using gin (metadata);

-- ===== 11_document_ingestion_job.sql =====
-- 文档异步入库任务表。

create extension if not exists "uuid-ossp";

create table if not exists public.document_ingestion_job (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    source_id varchar(128) not null,
    file_name varchar(255) not null,
    file_path text not null,
    title varchar(512),
    status varchar(32) not null default 'PENDING',
    progress int not null default 0,
    error_message text,
    retry_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz,

    constraint chk_document_ingestion_job_status check (status in ('PENDING', 'QUEUED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'INDEXED', 'FAILED')),
    constraint chk_document_ingestion_job_progress check (progress between 0 and 100),
    constraint chk_document_ingestion_job_retry_count check (retry_count >= 0)
);

comment on table public.document_ingestion_job is '文档异步入库任务表';
comment on column public.document_ingestion_job.file_path is '已持久化上传原始文件路径';
comment on column public.document_ingestion_job.status is '任务状态：PENDING、QUEUED、PARSING、CHUNKING、EMBEDDING、INDEXING、INDEXED、FAILED';

create index if not exists idx_document_ingestion_job_owner_created_at
    on public.document_ingestion_job using btree (owner_user_id, created_at desc);

create index if not exists idx_document_ingestion_job_owner_source
    on public.document_ingestion_job using btree (owner_user_id, source_id);

create index if not exists idx_document_ingestion_job_status
    on public.document_ingestion_job using btree (status);

-- ===== README.sql =====
-- RAG 数据表总入口。
-- 这个单文件版本已汇总全部 SQL 脚本，适合一次性导入数据库。
--
-- 原始目录式初始化说明在历史版本中保留；如果只使用此单文件，可直接执行本文件。
--
-- 本地运行：
--   docker compose up -d
--   copy src/main/resources/application-local.example.yaml src/main/resources/application-local.yaml
--   $env:DASHSCOPE_API_KEY="your-api-key"
--   mvn spring-boot:run
--
-- 上传验证：
--   curl -F "file=@sample.pdf" -F "sourceId=sample-paper" -F "title=Sample Paper" http://localhost:8080/documents
--
-- 问答验证：
--   curl -H "Content-Type: application/json" -d '{"question":"这篇论文的核心观点是什么？","topK":3}' http://localhost:8080/rag/ask