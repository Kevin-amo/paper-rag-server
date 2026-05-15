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