package com.lqr.paperragserver.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户角色关联实体，用于维护一个用户拥有的角色集合。
 */
@Data
@TableName("public.sys_user_role")
public class SysUserRoleEntity {

    private UUID userId;
    private UUID roleId;
    private OffsetDateTime createdAt;
}