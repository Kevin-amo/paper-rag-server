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