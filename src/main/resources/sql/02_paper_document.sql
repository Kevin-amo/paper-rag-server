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

    constraint chk_paper_document_status check (status in ('PENDING', 'PARSING', 'INDEXED', 'FAILED', 'DELETED')),
    constraint chk_paper_document_publish_year check (publish_year is null or publish_year between 1500 and 3000),
    constraint chk_paper_document_chunk_count check (chunk_count >= 0)
);

comment on table public.paper_document is '论文/文档主表，保存文档级元数据和入库状态';
comment on column public.paper_document.owner_user_id is '文档归属用户 ID';
comment on column public.paper_document.source_id is '文档来源唯一标识，对应代码中的 DocumentSource.sourceId';
comment on column public.paper_document.content_text is '解析后的全文文本，便于重建索引和问题排查';
comment on column public.paper_document.metadata is '文档级扩展元数据';
comment on column public.paper_document.status is '入库状态：PENDING、PARSING、INDEXED、FAILED、DELETED';

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