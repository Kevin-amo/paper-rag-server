-- 为已有系统用户表补充头像字段。

alter table if exists public.sys_user
    add column if not exists avatar_object_key varchar(512),
    add column if not exists avatar_updated_at timestamptz;

comment on column public.sys_user.avatar_object_key is '用户头像在对象存储中的 object key';
comment on column public.sys_user.avatar_updated_at is '用户头像最近更新时间';