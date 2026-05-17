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