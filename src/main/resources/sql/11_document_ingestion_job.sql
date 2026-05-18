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