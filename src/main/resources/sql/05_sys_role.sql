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