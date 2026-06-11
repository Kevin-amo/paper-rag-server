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

    constraint chk_sys_role_code check (code in ('ADMIN', 'USER', 'REVIEWER'))
);

comment on table public.sys_role is '系统角色表';
comment on column public.sys_role.code is '角色编码：ADMIN、USER、REVIEWER';

alter table if exists public.sys_role
    drop constraint if exists chk_sys_role_code;

alter table if exists public.sys_role
    add constraint chk_sys_role_code check (code in ('ADMIN', 'USER', 'REVIEWER'));

insert into public.sys_role (code, name, description)
values
    ('ADMIN', '管理员', '拥有文档管理和用户管理权限'),
    ('USER', '普通用户', '拥有文档读取和 RAG 问答权限'),
    ('REVIEWER', '评审员', '拥有论文辅助评审和评审报告调整权限')
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

comment on table public.conversation is 'Agent 用户会话表';
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
    metadata jsonb,
    created_at timestamptz not null default now(),

    constraint chk_conversation_message_role check (role in ('USER', 'ASSISTANT')),
    constraint uk_conversation_message_order unique (conversation_id, message_order)
);

comment on table public.conversation_message is 'RAG Agent 会话消息表';
comment on column public.conversation_message.role is '消息角色：USER、ASSISTANT';
comment on column public.conversation_message.citations is 'Assistant 回答引用信息 JSON';
comment on column public.conversation_message.metadata is '会话消息扩展元数据 JSON';

create index if not exists idx_conversation_message_owner_conversation_order
    on public.conversation_message using btree (owner_user_id, conversation_id, message_order);

create index if not exists idx_conversation_message_conversation_created_at
    on public.conversation_message using btree (conversation_id, created_at);

alter table if exists public.conversation_message
    add column if not exists metadata jsonb;

comment on column public.conversation_message.metadata is '会话消息扩展元数据 JSON';

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

-- ===== 12_paper_structured_parse.sql =====
-- 论文结构化解析结果表。

create extension if not exists "uuid-ossp";

create table if not exists public.paper_structured_parse (
    id uuid primary key default uuid_generate_v4(),
    owner_user_id uuid not null references public.sys_user(id) on delete cascade,
    document_id uuid not null references public.paper_document(id) on delete cascade,
    source_id varchar(128) not null,
    raw_text text,

    rule_result jsonb not null default '{}'::jsonb,
    model_result jsonb not null default '{}'::jsonb,
    merged_result jsonb not null default '{}'::jsonb,
    field_confidence jsonb not null default '{}'::jsonb,
    missing_fields jsonb not null default '[]'::jsonb,
    low_confidence_fields jsonb not null default '[]'::jsonb,
    raw_model_output text,
    parser_version varchar(64),
    model_version varchar(128),
    prompt_version varchar(64),
    quality_metrics jsonb not null default '{}'::jsonb,

    status varchar(32) not null default 'PENDING',
    error_message text,
    parsed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint fk_paper_structured_parse_source
        foreign key (owner_user_id, source_id)
        references public.paper_document (owner_user_id, source_id)
        on update cascade
        on delete cascade,

    constraint uq_paper_structured_parse_owner_source unique (owner_user_id, source_id),
    constraint chk_paper_structured_parse_status check (status in ('PENDING', 'RULE_PARSED', 'MODEL_COMPLETED', 'COMPLETED', 'FAILED'))
);

alter table public.paper_structured_parse
    add column if not exists parser_version varchar(64),
    add column if not exists model_version varchar(128),
    add column if not exists prompt_version varchar(64),
    add column if not exists quality_metrics jsonb not null default '{}'::jsonb;

comment on table public.paper_structured_parse is '论文结构化解析结果表';
comment on column public.paper_structured_parse.raw_text is '结构化解析所依据的全文快照';
comment on column public.paper_structured_parse.rule_result is '规则章节识别结果 JSON';
comment on column public.paper_structured_parse.model_result is '模型补全结果 JSON';
comment on column public.paper_structured_parse.merged_result is '最终合并后的结构化解析结果 JSON';
comment on column public.paper_structured_parse.field_confidence is '字段级来源、置信度和证据 JSON';
comment on column public.paper_structured_parse.parser_version is '结构化解析器版本';
comment on column public.paper_structured_parse.model_version is '模型版本';
comment on column public.paper_structured_parse.prompt_version is '提示词版本';
comment on column public.paper_structured_parse.quality_metrics is '结构化解析质量指标 JSON';
comment on column public.paper_structured_parse.status is '解析状态：PENDING、RULE_PARSED、MODEL_COMPLETED、COMPLETED、FAILED';

create index if not exists idx_paper_structured_parse_owner_source
    on public.paper_structured_parse using btree (owner_user_id, source_id);

create index if not exists idx_paper_structured_parse_document_id
    on public.paper_structured_parse using btree (document_id);

create index if not exists idx_paper_structured_parse_status
    on public.paper_structured_parse using btree (status);

-- ===== 13_review.sql =====
-- 论文辅助评审数据表。

create extension if not exists "uuid-ossp";

create table if not exists public.review_criterion (
    id uuid primary key default uuid_generate_v4(),
    code varchar(64) not null unique,
    name varchar(128) not null,
    description text,
    max_score int not null default 100,
    weight int not null default 20,
    version int not null default 1,
    category varchar(64),
    evidence_required boolean not null default true,
    scoring_rules jsonb not null default '[]'::jsonb,
    enabled boolean not null default true,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint chk_review_criterion_max_score check (max_score between 1 and 100),
    constraint chk_review_criterion_weight check (weight between 1 and 100)
);

alter table public.review_criterion
    add column if not exists version int not null default 1,
    add column if not exists category varchar(64),
    add column if not exists evidence_required boolean not null default true,
    add column if not exists scoring_rules jsonb not null default '[]'::jsonb;

comment on table public.review_criterion is '论文评审指标配置表';
comment on column public.review_criterion.code is '评审指标编码';
comment on column public.review_criterion.weight is '指标权重，用于页面展示和评分参考';

insert into public.review_criterion (
    code, name, description, max_score, weight, version, category, evidence_required, scoring_rules, enabled, sort_order
)
values
    ('POLICY', '政策导向', '检查论文是否存在政治不当表述、价值导向偏差或敏感风险。', 100, 20, 1, 'CONTENT', true, '[]'::jsonb, true, 10),
    ('MATCH', '专业匹配', '判断研究主题、研究对象和方法路径是否符合申报方向或评审场景。', 100, 20, 1, 'CONTENT', true, '[]'::jsonb, true, 20),
    ('INNOVATION', '创新性', '评价选题、方法、数据、结论或应用场景是否具有新意。', 100, 20, 1, 'QUALITY', true, '[]'::jsonb, true, 30),
    ('LOGIC', '逻辑性', '评价论文结构、论证链路、方法与结论之间的连贯性。', 100, 20, 1, 'QUALITY', true, '[]'::jsonb, true, 40),
    ('LANGUAGE', '语言质量', '评价表达准确性、学术规范性、语句流畅度和错别字风险。', 100, 20, 1, 'QUALITY', true, '[]'::jsonb, true, 50),
    ('REFERENCE', '参考文献规范', '检查参考文献格式、引用完整性和明显缺失问题。', 100, 10, 1, 'FORMAT', true, '[]'::jsonb, true, 60)
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    max_score = excluded.max_score,
    weight = excluded.weight,
    version = excluded.version,
    category = excluded.category,
    evidence_required = excluded.evidence_required,
    scoring_rules = excluded.scoring_rules,
    enabled = excluded.enabled,
    sort_order = excluded.sort_order,
    updated_at = now();

create table if not exists public.review_batch (
    id uuid primary key default uuid_generate_v4(),
    name varchar(160) not null,
    description text,
    status varchar(32) not null default 'DRAFT',
    starts_at timestamptz,
    ends_at timestamptz,
    created_by_user_id uuid references public.sys_user(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_review_batch_status check (status in ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'))
);

comment on table public.review_batch is '评审批次表';
comment on column public.review_batch.status is '批次状态：DRAFT、ACTIVE、CLOSED、ARCHIVED';

create index if not exists idx_review_batch_status_updated_at
    on public.review_batch using btree (status, updated_at desc);

create table if not exists public.review_group (
    id uuid primary key default uuid_generate_v4(),
    batch_id uuid not null references public.review_batch(id) on delete cascade,
    name varchar(160) not null,
    leader_user_id uuid not null references public.sys_user(id) on delete restrict,
    status varchar(32) not null default 'ACTIVE',
    created_by_user_id uuid references public.sys_user(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_review_group_status check (status in ('ACTIVE', 'DISABLED'))
);

comment on table public.review_group is '评审小组表';
comment on column public.review_group.leader_user_id is '小组长用户 ID';

create index if not exists idx_review_group_batch_status
    on public.review_group using btree (batch_id, status);
create index if not exists idx_review_group_leader_status
    on public.review_group using btree (leader_user_id, status);

create table if not exists public.review_group_member (
    id uuid primary key default uuid_generate_v4(),
    group_id uuid not null references public.review_group(id) on delete cascade,
    user_id uuid not null references public.sys_user(id) on delete cascade,
    member_role varchar(32) not null default 'REVIEWER',
    status varchar(32) not null default 'ACTIVE',
    joined_at timestamptz not null default now(),
    removed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_review_group_member_role check (member_role in ('LEADER', 'REVIEWER')),
    constraint chk_review_group_member_status check (status in ('ACTIVE', 'REMOVED'))
);

comment on table public.review_group_member is '评审小组成员表';

create unique index if not exists uq_review_group_member_active
    on public.review_group_member (group_id, user_id)
    where status = 'ACTIVE';
create index if not exists idx_review_group_member_user_status
    on public.review_group_member using btree (user_id, status);

create table if not exists public.review_task (
    id uuid primary key default uuid_generate_v4(),
    document_id uuid not null references public.paper_document(id) on delete cascade,
    submitter_user_id uuid not null references public.sys_user(id) on delete cascade,
    reviewer_user_id uuid references public.sys_user(id) on delete set null,
    batch_id uuid references public.review_batch(id) on delete set null,
    group_id uuid references public.review_group(id) on delete set null,
    assigned_by_user_id uuid references public.sys_user(id) on delete set null,
    leader_user_id uuid references public.sys_user(id) on delete set null,
    source_id varchar(128) not null,
    title varchar(512) not null,
    status varchar(32) not null default 'PENDING_ASSIGNMENT',
    assigned_at timestamptz,
    due_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uq_review_task_document unique (document_id),
    constraint chk_review_task_status check (status in ('PENDING_ASSIGNMENT', 'ASSIGNED', 'IN_REVIEW', 'SUBMITTED', 'CONSENSUS_CONFIRMED', 'NEEDS_REVIEW'))
);

comment on table public.review_task is '论文评审任务表';
comment on column public.review_task.status is '评审状态：PENDING_ASSIGNMENT、ASSIGNED、IN_REVIEW、SUBMITTED、CONSENSUS_CONFIRMED、NEEDS_REVIEW';

create index if not exists idx_review_task_status_updated_at
    on public.review_task using btree (status, updated_at desc);

create index if not exists idx_review_task_submitter
    on public.review_task using btree (submitter_user_id, updated_at desc);

create index if not exists idx_review_task_reviewer
    on public.review_task using btree (reviewer_user_id, updated_at desc);

create index if not exists idx_review_task_batch_group_status
    on public.review_task using btree (batch_id, group_id, status, updated_at desc);

create index if not exists idx_review_task_leader_status
    on public.review_task using btree (leader_user_id, status, updated_at desc);

create table if not exists public.review_assignment (
    id uuid primary key default uuid_generate_v4(),
    task_id uuid not null references public.review_task(id) on delete cascade,
    reviewer_user_id uuid not null references public.sys_user(id) on delete cascade,
    group_id uuid references public.review_group(id) on delete set null,
    assigned_by_user_id uuid references public.sys_user(id) on delete set null,
    role varchar(32) not null default 'REVIEWER',
    status varchar(32) not null default 'ASSIGNED',
    assigned_at timestamptz not null default now(),
    due_at timestamptz,
    submitted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_review_assignment_task_reviewer unique (task_id, reviewer_user_id),
    constraint chk_review_assignment_role check (role in ('LEAD', 'REVIEWER', 'ARBITER')),
    constraint chk_review_assignment_status check (status in ('ASSIGNED', 'REVIEWING', 'SUBMITTED', 'RETURNED', 'CANCELLED'))
);
create index if not exists idx_review_assignment_task_status on public.review_assignment using btree (task_id, status);
create index if not exists idx_review_assignment_reviewer_status on public.review_assignment using btree (reviewer_user_id, status);
create index if not exists idx_review_assignment_group_status on public.review_assignment using btree (group_id, status);
create unique index if not exists uq_review_assignment_task_active_lead
    on public.review_assignment (task_id)
    where role = 'LEAD' and status <> 'CANCELLED';

create table if not exists public.review_report (
    id uuid primary key default uuid_generate_v4(),
    task_id uuid not null references public.review_task(id) on delete cascade,
    document_id uuid not null references public.paper_document(id) on delete cascade,
    reviewer_user_id uuid references public.sys_user(id) on delete set null,
    assignment_id uuid references public.review_assignment(id) on delete cascade,
    paper_sections jsonb not null default '{}'::jsonb,
    scores jsonb not null default '[]'::jsonb,
    comments jsonb not null default '{}'::jsonb,
    risks jsonb not null default '[]'::jsonb,
    raw_model_output jsonb not null default '{}'::jsonb,
    criterion_version int,
    model_version varchar(128),
    prompt_version varchar(64),
    confidence numeric(5,4),
    manual_delta jsonb not null default '{}'::jsonb,
    total_score int,
    final_recommendation text,
    status varchar(32) not null default 'AI_GENERATED',
    generated_at timestamptz,
    adjusted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint chk_review_report_total_score check (total_score is null or total_score between 0 and 100),
    constraint chk_review_report_confidence check (confidence is null or (confidence >= 0 and confidence <= 1)),
    constraint chk_review_report_status check (status in ('AI_GENERATED', 'ADJUSTED', 'CONFIRMED', 'COMPLETED'))
);

comment on table public.review_report is '论文辅助评审报告表';
comment on column public.review_report.paper_sections is '模型结构化解析结果';
comment on column public.review_report.scores is '多维评分结果';
comment on column public.review_report.risks is '风险提示结果';

create index if not exists idx_review_report_task_updated_at
    on public.review_report using btree (task_id, updated_at desc);

create index if not exists idx_review_report_reviewer
    on public.review_report using btree (reviewer_user_id, updated_at desc);

create unique index if not exists uq_review_report_assignment
    on public.review_report (assignment_id)
    where assignment_id is not null;
create index if not exists idx_review_report_task_reviewer
    on public.review_report using btree (task_id, reviewer_user_id);

create table if not exists public.review_risk_item (
    id uuid primary key default uuid_generate_v4(),
    report_id uuid not null references public.review_report(id) on delete cascade,
    task_id uuid not null references public.review_task(id) on delete cascade,
    risk_type varchar(64) not null,
    risk_level varchar(32) not null,
    evidence text,
    evidence_location jsonb not null default '{}'::jsonb,
    suggestion text,
    detector varchar(64),
    confidence numeric(5,4),
    status varchar(32) not null default 'OPEN',
    reviewer_note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_review_risk_level check (risk_level in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint chk_review_risk_confidence check (confidence is null or (confidence >= 0 and confidence <= 1)),
    constraint chk_review_risk_status check (status in ('OPEN', 'CONFIRMED', 'IGNORED', 'RESOLVED'))
);

create index if not exists idx_review_risk_item_report_status on public.review_risk_item using btree (report_id, status);
create index if not exists idx_review_risk_item_task_level on public.review_risk_item using btree (task_id, risk_level);

create table if not exists public.review_consensus (
    id uuid primary key default uuid_generate_v4(),
    task_id uuid not null references public.review_task(id) on delete cascade,
    lead_reviewer_user_id uuid references public.sys_user(id) on delete set null,
    score_summary jsonb not null default '{}'::jsonb,
    comment_summary jsonb not null default '{}'::jsonb,
    disagreement_items jsonb not null default '[]'::jsonb,
    final_score int,
    final_recommendation text,
    status varchar(32) not null default 'DRAFT',
    confirmed_by_user_id uuid references public.sys_user(id) on delete set null,
    confirmed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_review_consensus_task unique (task_id),
    constraint chk_review_consensus_score check (final_score is null or final_score between 0 and 100),
    constraint chk_review_consensus_status check (status in ('DRAFT', 'IN_DISCUSSION', 'CONFIRMED', 'ARCHIVED'))
);
create index if not exists idx_review_consensus_status on public.review_consensus using btree (status);

create table if not exists public.review_audit_log (
    id uuid primary key default uuid_generate_v4(),
    task_id uuid not null references public.review_task(id) on delete cascade,
    operator_user_id uuid references public.sys_user(id) on delete set null,
    action varchar(64) not null,
    note text,
    snapshot jsonb not null default '{}'::jsonb,
    before_snapshot jsonb not null default '{}'::jsonb,
    after_snapshot jsonb not null default '{}'::jsonb,
    diff jsonb not null default '{}'::jsonb,
    client_info jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

comment on table public.review_audit_log is '论文评审操作留档表';

create index if not exists idx_review_audit_log_task_created_at
    on public.review_audit_log using btree (task_id, created_at desc);

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

-- ===== column-comments.sql =====
-- 统一字段中文注释。放在脚本末尾以覆盖前面不完整或旧版本的字段注释。

comment on column public.sys_role.id is '角色主键 ID';
comment on column public.sys_role.code is '角色编码';
comment on column public.sys_role.name is '角色名称';
comment on column public.sys_role.description is '角色描述';
comment on column public.sys_role.created_at is '创建时间';

comment on column public.sys_user.id is '用户主键 ID';
comment on column public.sys_user.username is '登录用户名';
comment on column public.sys_user.password_hash is '密码哈希值';
comment on column public.sys_user.display_name is '用户显示名称';
comment on column public.sys_user.email is '邮箱地址';
comment on column public.sys_user.phone is '手机号码';
comment on column public.sys_user.avatar_object_key is '头像对象存储键';
comment on column public.sys_user.avatar_updated_at is '头像更新时间';
comment on column public.sys_user.status is '用户状态';
comment on column public.sys_user.last_login_at is '最后登录时间';
comment on column public.sys_user.created_at is '创建时间';
comment on column public.sys_user.updated_at is '更新时间';

comment on column public.sys_user_role.user_id is '用户 ID';
comment on column public.sys_user_role.role_id is '角色 ID';
comment on column public.sys_user_role.created_at is '创建时间';

comment on column public.conversation.id is '会话主键 ID';
comment on column public.conversation.owner_user_id is '会话所属用户 ID';
comment on column public.conversation.title is '会话标题';
comment on column public.conversation.created_at is '创建时间';
comment on column public.conversation.updated_at is '更新时间';
comment on column public.conversation.deleted_at is '软删除时间';

comment on column public.conversation_message.id is '消息主键 ID';
comment on column public.conversation_message.conversation_id is '所属会话 ID';
comment on column public.conversation_message.owner_user_id is '消息所属用户 ID';
comment on column public.conversation_message.role is '消息角色';
comment on column public.conversation_message.message_order is '消息顺序号';
comment on column public.conversation_message.content is '消息内容';
comment on column public.conversation_message.citations is '引用来源 JSON';
comment on column public.conversation_message.metadata is '消息元数据 JSON';
comment on column public.conversation_message.created_at is '创建时间';

comment on column public.vector_store.id is '向量主键 ID';
comment on column public.vector_store.content is '向量对应文本内容';
comment on column public.vector_store.metadata is '向量元数据 JSON';
comment on column public.vector_store.embedding is '文本嵌入向量';

comment on column public.paper_document.id is '文档主键 ID';
comment on column public.paper_document.owner_user_id is '文档所属用户 ID';
comment on column public.paper_document.source_id is '文档来源 ID';
comment on column public.paper_document.title is '文档标题';
comment on column public.paper_document.origin is '文档来源说明';
comment on column public.paper_document.file_name is '原始文件名';
comment on column public.paper_document.file_type is '文件类型';
comment on column public.paper_document.file_size is '文件大小字节数';
comment on column public.paper_document.authors is '作者列表 JSON';
comment on column public.paper_document.abstract is '论文摘要';
comment on column public.paper_document.doi is 'DOI 标识';
comment on column public.paper_document.journal is '期刊或会议名称';
comment on column public.paper_document.publish_year is '发表年份';
comment on column public.paper_document.keywords is '关键词列表 JSON';
comment on column public.paper_document.content_text is '文档全文文本';
comment on column public.paper_document.metadata is '文档元数据 JSON';
comment on column public.paper_document.status is '文档处理状态';
comment on column public.paper_document.chunk_count is '文档分块数量';
comment on column public.paper_document.error_message is '处理错误信息';
comment on column public.paper_document.created_at is '创建时间';
comment on column public.paper_document.updated_at is '更新时间';
comment on column public.paper_document.deleted_at is '软删除时间';

comment on column public.paper_document_chunk.id is '文档分块主键 ID';
comment on column public.paper_document_chunk.owner_user_id is '分块所属用户 ID';
comment on column public.paper_document_chunk.chunk_id is '业务分块 ID';
comment on column public.paper_document_chunk.source_id is '文档来源 ID';
comment on column public.paper_document_chunk.chunk_index is '分块序号';
comment on column public.paper_document_chunk.content is '分块文本内容';
comment on column public.paper_document_chunk.content_hash is '分块内容哈希';
comment on column public.paper_document_chunk.chunk_start is '分块起始字符位置';
comment on column public.paper_document_chunk.chunk_end is '分块结束字符位置';
comment on column public.paper_document_chunk.page_number is '分块所在页码';
comment on column public.paper_document_chunk.section_title is '分块所属章节标题';
comment on column public.paper_document_chunk.metadata is '分块元数据 JSON';
comment on column public.paper_document_chunk.vector_store_id is '关联向量 ID';
comment on column public.paper_document_chunk.created_at is '创建时间';
comment on column public.paper_document_chunk.updated_at is '更新时间';

comment on column public.paper_document_asset.id is '文档资源主键 ID';
comment on column public.paper_document_asset.owner_user_id is '资源所属用户 ID';
comment on column public.paper_document_asset.asset_id is '业务资源 ID';
comment on column public.paper_document_asset.source_id is '文档来源 ID';
comment on column public.paper_document_asset.asset_index is '资源序号';
comment on column public.paper_document_asset.asset_type is '资源类型';
comment on column public.paper_document_asset.file_name is '资源文件名';
comment on column public.paper_document_asset.content_type is '资源 MIME 类型';
comment on column public.paper_document_asset.file_size is '资源文件大小字节数';
comment on column public.paper_document_asset.content_hash is '资源内容哈希';
comment on column public.paper_document_asset.content is '资源二进制内容';
comment on column public.paper_document_asset.extracted_text is '资源提取文本';
comment on column public.paper_document_asset.text_start is '资源文本起始字符位置';
comment on column public.paper_document_asset.text_end is '资源文本结束字符位置';
comment on column public.paper_document_asset.metadata is '资源元数据 JSON';
comment on column public.paper_document_asset.created_at is '创建时间';
comment on column public.paper_document_asset.updated_at is '更新时间';

comment on column public.document_ingestion_job.id is '文档导入任务主键 ID';
comment on column public.document_ingestion_job.owner_user_id is '任务所属用户 ID';
comment on column public.document_ingestion_job.source_id is '文档来源 ID';
comment on column public.document_ingestion_job.file_name is '导入文件名';
comment on column public.document_ingestion_job.file_path is '导入文件路径';
comment on column public.document_ingestion_job.title is '导入文档标题';
comment on column public.document_ingestion_job.status is '导入任务状态';
comment on column public.document_ingestion_job.progress is '导入进度百分比';
comment on column public.document_ingestion_job.error_message is '导入错误信息';
comment on column public.document_ingestion_job.retry_count is '重试次数';
comment on column public.document_ingestion_job.created_at is '创建时间';
comment on column public.document_ingestion_job.updated_at is '更新时间';
comment on column public.document_ingestion_job.started_at is '任务开始时间';
comment on column public.document_ingestion_job.finished_at is '任务完成时间';

comment on column public.paper_structured_parse.id is '结构化解析主键 ID';
comment on column public.paper_structured_parse.owner_user_id is '解析所属用户 ID';
comment on column public.paper_structured_parse.document_id is '关联文档 ID';
comment on column public.paper_structured_parse.source_id is '文档来源 ID';
comment on column public.paper_structured_parse.raw_text is '原始解析文本';
comment on column public.paper_structured_parse.rule_result is '规则解析结果 JSON';
comment on column public.paper_structured_parse.model_result is '模型补全结果 JSON';
comment on column public.paper_structured_parse.merged_result is '合并后的结构化结果 JSON';
comment on column public.paper_structured_parse.field_confidence is '字段置信度 JSON';
comment on column public.paper_structured_parse.missing_fields is '缺失字段列表 JSON';
comment on column public.paper_structured_parse.low_confidence_fields is '低置信字段列表 JSON';
comment on column public.paper_structured_parse.raw_model_output is '模型原始输出';
comment on column public.paper_structured_parse.parser_version is '结构化解析器版本';
comment on column public.paper_structured_parse.model_version is '模型版本';
comment on column public.paper_structured_parse.prompt_version is '提示词版本';
comment on column public.paper_structured_parse.quality_metrics is '结构化解析质量指标 JSON';
comment on column public.paper_structured_parse.status is '解析状态';
comment on column public.paper_structured_parse.error_message is '解析错误信息';
comment on column public.paper_structured_parse.parsed_at is '解析完成时间';
comment on column public.paper_structured_parse.created_at is '创建时间';
comment on column public.paper_structured_parse.updated_at is '更新时间';

comment on column public.review_criterion.id is '评审指标主键 ID';
comment on column public.review_criterion.code is '评审指标编码';
comment on column public.review_criterion.name is '评审指标名称';
comment on column public.review_criterion.description is '评审指标描述';
comment on column public.review_criterion.max_score is '指标最高分';
comment on column public.review_criterion.weight is '指标权重';
comment on column public.review_criterion.version is '评审指标版本';
comment on column public.review_criterion.category is '评审指标分类';
comment on column public.review_criterion.evidence_required is '是否要求填写证据';
comment on column public.review_criterion.scoring_rules is '评分规则 JSON';
comment on column public.review_criterion.enabled is '是否启用';
comment on column public.review_criterion.sort_order is '排序序号';
comment on column public.review_criterion.created_at is '创建时间';
comment on column public.review_criterion.updated_at is '更新时间';

comment on column public.review_batch.id is '评审批次主键 ID';
comment on column public.review_batch.name is '评审批次名称';
comment on column public.review_batch.description is '评审批次描述';
comment on column public.review_batch.status is '批次状态';
comment on column public.review_batch.starts_at is '批次开始时间';
comment on column public.review_batch.ends_at is '批次结束时间';
comment on column public.review_batch.created_by_user_id is '批次创建人用户 ID';
comment on column public.review_batch.created_at is '创建时间';
comment on column public.review_batch.updated_at is '更新时间';

comment on column public.review_group.id is '评审小组主键 ID';
comment on column public.review_group.batch_id is '所属评审批次 ID';
comment on column public.review_group.name is '评审小组名称';
comment on column public.review_group.leader_user_id is '小组长用户 ID';
comment on column public.review_group.status is '小组状态';
comment on column public.review_group.created_by_user_id is '小组创建人用户 ID';
comment on column public.review_group.created_at is '创建时间';
comment on column public.review_group.updated_at is '更新时间';

comment on column public.review_group_member.id is '小组成员主键 ID';
comment on column public.review_group_member.group_id is '所属评审小组 ID';
comment on column public.review_group_member.user_id is '成员用户 ID';
comment on column public.review_group_member.member_role is '成员角色';
comment on column public.review_group_member.status is '成员状态';
comment on column public.review_group_member.joined_at is '加入时间';
comment on column public.review_group_member.removed_at is '移除时间';
comment on column public.review_group_member.created_at is '创建时间';
comment on column public.review_group_member.updated_at is '更新时间';

comment on column public.review_task.id is '评审任务主键 ID';
comment on column public.review_task.document_id is '关联文档 ID';
comment on column public.review_task.submitter_user_id is '提交人用户 ID';
comment on column public.review_task.reviewer_user_id is '评审人用户 ID';
comment on column public.review_task.batch_id is '所属评审批次 ID';
comment on column public.review_task.group_id is '所属评审小组 ID';
comment on column public.review_task.assigned_by_user_id is '分配人用户 ID';
comment on column public.review_task.leader_user_id is '小组长用户 ID';
comment on column public.review_task.source_id is '文档来源 ID';
comment on column public.review_task.title is '评审任务标题';
comment on column public.review_task.status is '评审任务状态';
comment on column public.review_task.assigned_at is '分配时间';
comment on column public.review_task.due_at is '截止时间';
comment on column public.review_task.completed_at is '完成时间';
comment on column public.review_task.created_at is '创建时间';
comment on column public.review_task.updated_at is '更新时间';

comment on column public.review_assignment.id is '评审分配主键 ID';
comment on column public.review_assignment.task_id is '所属评审任务 ID';
comment on column public.review_assignment.reviewer_user_id is '评审人用户 ID';
comment on column public.review_assignment.group_id is '所属评审小组 ID';
comment on column public.review_assignment.assigned_by_user_id is '分配人用户 ID';
comment on column public.review_assignment.role is '评审分配角色';
comment on column public.review_assignment.status is '评审分配状态';
comment on column public.review_assignment.assigned_at is '分配时间';
comment on column public.review_assignment.due_at is '截止时间';
comment on column public.review_assignment.submitted_at is '提交时间';
comment on column public.review_assignment.created_at is '创建时间';
comment on column public.review_assignment.updated_at is '更新时间';

comment on column public.review_report.id is '评审报告主键 ID';
comment on column public.review_report.task_id is '所属评审任务 ID';
comment on column public.review_report.document_id is '关联文档 ID';
comment on column public.review_report.reviewer_user_id is '评审人用户 ID';
comment on column public.review_report.assignment_id is '关联评审分配 ID';
comment on column public.review_report.paper_sections is '论文分段信息 JSON';
comment on column public.review_report.scores is '评分明细 JSON';
comment on column public.review_report.comments is '评审意见 JSON';
comment on column public.review_report.risks is '风险项列表 JSON';
comment on column public.review_report.raw_model_output is '模型原始输出 JSON';
comment on column public.review_report.criterion_version is '评审指标版本';
comment on column public.review_report.model_version is '模型版本';
comment on column public.review_report.prompt_version is '提示词版本';
comment on column public.review_report.confidence is '报告置信度';
comment on column public.review_report.manual_delta is '人工修改差异 JSON';
comment on column public.review_report.total_score is '总分';
comment on column public.review_report.final_recommendation is '最终建议';
comment on column public.review_report.status is '评审报告状态';
comment on column public.review_report.generated_at is '报告生成时间';
comment on column public.review_report.adjusted_at is '人工调整时间';
comment on column public.review_report.created_at is '创建时间';
comment on column public.review_report.updated_at is '更新时间';

comment on column public.review_risk_item.id is '风险项主键 ID';
comment on column public.review_risk_item.report_id is '所属评审报告 ID';
comment on column public.review_risk_item.task_id is '所属评审任务 ID';
comment on column public.review_risk_item.risk_type is '风险类型';
comment on column public.review_risk_item.risk_level is '风险等级';
comment on column public.review_risk_item.evidence is '风险证据';
comment on column public.review_risk_item.evidence_location is '证据位置 JSON';
comment on column public.review_risk_item.suggestion is '处理建议';
comment on column public.review_risk_item.detector is '风险检测器';
comment on column public.review_risk_item.confidence is '风险置信度';
comment on column public.review_risk_item.status is '风险项状态';
comment on column public.review_risk_item.reviewer_note is '评审人备注';
comment on column public.review_risk_item.created_at is '创建时间';
comment on column public.review_risk_item.updated_at is '更新时间';

comment on column public.review_consensus.id is '评审共识主键 ID';
comment on column public.review_consensus.task_id is '所属评审任务 ID';
comment on column public.review_consensus.lead_reviewer_user_id is '主评审人用户 ID';
comment on column public.review_consensus.score_summary is '评分汇总 JSON';
comment on column public.review_consensus.comment_summary is '意见汇总 JSON';
comment on column public.review_consensus.disagreement_items is '分歧项列表 JSON';
comment on column public.review_consensus.final_score is '最终分数';
comment on column public.review_consensus.final_recommendation is '最终建议';
comment on column public.review_consensus.status is '共识状态';
comment on column public.review_consensus.confirmed_by_user_id is '确认人用户 ID';
comment on column public.review_consensus.confirmed_at is '确认时间';
comment on column public.review_consensus.created_at is '创建时间';
comment on column public.review_consensus.updated_at is '更新时间';

comment on column public.review_audit_log.id is '审计日志主键 ID';
comment on column public.review_audit_log.task_id is '所属评审任务 ID';
comment on column public.review_audit_log.operator_user_id is '操作人用户 ID';
comment on column public.review_audit_log.action is '操作类型';
comment on column public.review_audit_log.note is '操作备注';
comment on column public.review_audit_log.snapshot is '操作快照 JSON';
comment on column public.review_audit_log.before_snapshot is '变更前快照 JSON';
comment on column public.review_audit_log.after_snapshot is '变更后快照 JSON';
comment on column public.review_audit_log.diff is '变更差异 JSON';
comment on column public.review_audit_log.client_info is '客户端信息 JSON';
comment on column public.review_audit_log.created_at is '创建时间';
--
-- 问答验证：
--   前端主链路通过 /agent/ask/stream 发起 agent 流式问答。
