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