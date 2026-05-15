-- 系统用户表。

create extension if not exists "uuid-ossp";

create table if not exists public.sys_user (
    id uuid primary key default uuid_generate_v4(),
    username varchar(64) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(128),
    email varchar(255),
    phone varchar(32),
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