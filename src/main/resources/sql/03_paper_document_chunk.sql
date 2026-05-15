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